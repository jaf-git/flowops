package com.flowops.nodepipeline.infrastructure.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowops.nodepipeline.application.port.WorkJudgePort;
import com.flowops.nodepipeline.domain.ai.Judgement;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component("ollamaWorkJudgeAdapter")
@ConditionalOnProperty(name = "flowops.ai.enabled", havingValue = "true")
public class OllamaWorkJudgeAdapter implements WorkJudgePort {
    private static final Logger LOG = LoggerFactory.getLogger(OllamaWorkJudgeAdapter.class);

    private static final String PROMPT_VERSION = "v3";

    private static final Map<String, Double> CONFIDENCE_WORTH = Map.of("HIGH", 0.9, "MEDIUM", 0.6, "LOW", 0.3);

    private static final String CONFIDENCE_RULE =
            "\n\nconfidence is exactly one of HIGH, MEDIUM or LOW. Not a number, not a percentage.\n";

    private static final int CONTEXT_TOKENS = 4096;

    /**
     * Fixed so that two runs over the same corpus reach the same answers. Its value carries no
     * meaning; that it never changes is the whole point, which is also why it is a constant rather
     * than a property -- a seed somebody can set per environment is a seed that differs between the
     * run in the thesis and the run on the examiner's machine.
     */
    private static final int SEED = 1;

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http;

    private final Map<String, Judgement.Verdict> cache = new ConcurrentHashMap<>();

    private final AtomicInteger callsUsed = new AtomicInteger();

    private final String url;
    private final String model;
    private final int budget;
    private final Map<Judgement.PlugPoint, Boolean> enabled;
    private final com.flowops.nodepipeline.application.AiSwitch aiSwitch;

    public OllamaWorkJudgeAdapter(
            @Value("${flowops.ai.ollama-url:http://localhost:11434}") String url,
            @Value("${flowops.ai.model:llama3.2:3b}") String model,
            @Value("${flowops.ai.timeout-seconds:20}") int timeoutSeconds,
            @Value("${flowops.ai.call-budget:200}") int budget,
            @Value("${flowops.ai.plug.same-work:false}") boolean sameWork,
            @Value("${flowops.ai.plug.same-kind:false}") boolean sameKind,
            @Value("${flowops.ai.plug.naming:false}") boolean naming,
            @Value("${flowops.ai.plug.same-activity:false}") boolean sameActivity,
            com.flowops.nodepipeline.application.AiSwitch aiSwitch) {
        this.url = url;
        this.model = model;
        this.budget = budget;
        this.aiSwitch = aiSwitch;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();

        this.enabled = Map.of(
                Judgement.PlugPoint.SAME_WORK, sameWork,
                Judgement.PlugPoint.SAME_KIND, sameKind,
                Judgement.PlugPoint.NAMING, naming,
                Judgement.PlugPoint.SAME_ACTIVITY, sameActivity,

                // EXPLAIN has no property of its own because it is not something a run does
                // behind anybody's back: it happens when a person presses a button and waits
                // for the answer. Consent is the press. The runtime switch still governs it,
                // so one control turns the model off everywhere.
                Judgement.PlugPoint.EXPLAIN, true);
    }

    @Override
    public void beginRun() {
        callsUsed.set(0);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean isEnabled(Judgement.PlugPoint plugPoint) {
        return aiSwitch.isOn() && enabled.getOrDefault(plugPoint, false);
    }

    @Override
    public Optional<Judgement.Verdict> judge(Judgement.Question question) {
        if (!isEnabled(question.plugPoint())) {
            return Optional.empty();
        }

        String key = cacheKey(question);
        Judgement.Verdict remembered = cache.get(key);
        if (remembered != null) {
            return Optional.of(remembered);
        }

        if (callsUsed.get() >= budget) {
            LOG.debug("ai_budget_exhausted after {} calls; falling back to the deterministic answer", budget);
            return Optional.empty();
        }

        try {
            callsUsed.incrementAndGet();
            Optional<Judgement.Verdict> verdict = ask(question);
            verdict.ifPresent(v -> cache.put(key, v));
            return verdict;
        } catch (Exception anyFailure) {
            LOG.debug("the model could not be used for {}: {}", question.plugPoint(), anyFailure.toString());
            return Optional.empty();
        }
    }

    @Override
    public int callsRemaining() {
        return Math.max(0, budget - callsUsed.get());
    }

    @Override
    public String modelId() {
        return model;
    }

    @Override
    public String promptVersion() {
        return PROMPT_VERSION;
    }

    private Optional<Judgement.Verdict> ask(Judgement.Question question) throws Exception {
        // Guidance is prose, so it is asked for as prose. Forcing it through a one-string JSON
        // schema truncated it to a heading -- the schema is right for a verdict and a label and
        // wrong for six paragraphs, and a longer budget is needed for the words themselves.
        boolean prose = question.plugPoint() == Judgement.PlugPoint.EXPLAIN;

        Map<String, Object> request = new java.util.LinkedHashMap<>(Map.of(
                "model",
                model,
                "prompt",
                promptFor(question),
                "stream",
                false,
                "options",
                // temperature 0 makes the sampler greedy; it does not make generation
                // reproducible on its own, because llama.cpp still varies with batching. Pinning
                // the seed as well is what makes two runs over the same corpus answer the same
                // way -- without it the same 200 nodes promoted 41 on one run and 39 on the next,
                // which is not a difference anybody could explain to a reader.
                prose
                        ? Map.of("temperature", 0, "seed", SEED, "num_ctx", CONTEXT_TOKENS, "num_predict", 700)
                        : Map.of("temperature", 0, "seed", SEED, "num_ctx", CONTEXT_TOKENS)));

        if (!prose) {
            request.put("format", json.readTree(answerShape(question.plugPoint())));
        }
        String body = json.writeValueAsString(request);

        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create(url + "/api/generate"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            return Optional.empty();
        }

        String said = json.readTree(response.body()).path("response").asText("");

        if (prose) {
            return said.isBlank() ? Optional.empty() : Optional.of(new Judgement.Verdict(said.trim(), 0.0, ""));
        }

        return validated(json.readTree(said), question.plugPoint());
    }

    private Optional<Judgement.Verdict> validated(JsonNode answer, Judgement.PlugPoint plugPoint) {
        String value = answer.path(plugPoint == Judgement.PlugPoint.NAMING ? "label" : "verdict")
                .asText(null);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        Double confidence =
                CONFIDENCE_WORTH.get(answer.path("confidence").asText("").toUpperCase(Locale.ROOT));
        if (confidence == null) {
            return Optional.empty();
        }

        // NAMING returns words a person will read and SAME_ACTIVITY returns an activity name that
        // has to match one already in the vocabulary character for character. Both are answers in
        // the workspace's own words; only the enum-valued plug points are folded to upper case.
        boolean answeredInWords =
                plugPoint == Judgement.PlugPoint.NAMING || plugPoint == Judgement.PlugPoint.SAME_ACTIVITY;

        String reason = answer.path("reason").asText("");
        Judgement.Verdict verdict =
                new Judgement.Verdict(answeredInWords ? value : value.toUpperCase(Locale.ROOT), confidence, reason);

        if (plugPoint == Judgement.PlugPoint.SAME_WORK && verdict.asSameWork().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(verdict);
    }

    private static String promptFor(Judgement.Question question) {
        return question.plugPoint() == Judgement.PlugPoint.EXPLAIN
                ? questionFor(question)
                : questionFor(question) + CONFIDENCE_RULE;
    }

    private static String questionFor(Judgement.Question question) {
        return switch (question.plugPoint()) {
            case SAME_WORK -> """
                    You are comparing one piece of work against task templates a business already has.

                    THE WORK: %s

                    THE CANDIDATES: %s

                    Answer SAME if the work is an instance of the best candidate, DIFFERENT if it is a
                    different kind of work, and UNSURE if you genuinely cannot tell. Prefer UNSURE over
                    a guess: silence is a correct answer here and a wrong match is not.
                    """
                    .formatted(question.text(), String.join(", ", question.candidateIds()));

            case SAME_KIND -> """
                    You are labelling one piece of work with the single concept it is about.

                    THE WORK: %s

                    ALLOWED CONCEPTS: %s

                    Put exactly one of the allowed concepts in verdict, or NONE if the work is not
                    clearly about any of them. NONE is a correct answer and is better than a guess:
                    a wrong label splits a group of work that belongs together.
                    """
                    .formatted(question.text(), question.context());
            case NAMING -> """
                    Name this recurring piece of work in two to four words.

                    WHAT PEOPLE WROTE: %s

                    Use only words that appear above, or the generic words draft, review, set and run.
                    Do not invent a name. Do not translate. Return the words themselves.
                    """
                    .formatted(question.text());

            case SAME_ACTIVITY -> """
                    You are comparing an activity somebody is typing against the activities a business
                    already names its work by.

                    WHAT THEY TYPED: %s

                    THE EXISTING ACTIVITIES: %s

                    Put the existing activity that names the same work in verdict, exactly as written
                    above, or NONE if none of them does. NONE is a correct answer and is better than a
                    guess: this is shown to the person as a question, and the cost of a wrong suggestion
                    is that two different activities become one.
                    """
                    .formatted(question.text(), String.join(", ", question.candidateIds()));

            case EXPLAIN -> """
                    You are writing a short handover note for somebody who joined this week and
                    has been asked to do this piece of work for the first time.

                    WHAT THIS WORK IS, as observed:
                    %s

                    Write it as plain guidance they can follow. Cover, in this order and only
                    where the observations above support it: what this work is for, what they
                    need before starting, the steps in the order they are done, who to go to,
                    and how they will know it is finished.

                    Rules. Use only what is above -- do not invent a step, a tool, a deadline or
                    a person. If something is not there, leave it out rather than guessing. Do
                    not translate names of work. Six short paragraphs at most, no headings, no
                    bullet characters, plain sentences. Write to the reader as "you".
                    """
                    .formatted(question.text());
        };
    }

    private static String answerShape(Judgement.PlugPoint plugPoint) {
        if (plugPoint == Judgement.PlugPoint.NAMING) {
            return """
                    {"type":"object",
                     "properties":{"label":{"type":"string"},"confidence":{"type":"string","enum":["HIGH","MEDIUM","LOW"]}},
                     "required":["label","confidence"]}
                    """;
        }
        return """
                {"type":"object",
                 "properties":{"verdict":{"type":"string"},
                               "confidence":{"type":"string","enum":["HIGH","MEDIUM","LOW"]},
                               "reason":{"type":"string"}},
                 "required":["verdict","confidence"]}
                """;
    }

    private String cacheKey(Judgement.Question question) {
        return Integer.toHexString(question.text().hashCode()) + "|"
                + String.join(",", question.candidateIds()) + "|"
                + PROMPT_VERSION + "|" + model;
    }
}
