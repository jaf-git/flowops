package com.flowops.chatassist.infrastructure.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flowops.chatassist.application.port.LocalLanguageModelPort;
import com.flowops.chatassist.domain.ConversationExtract;
import com.flowops.chatassist.domain.WorkOpinion;
import com.flowops.chatassist.domain.WorkShape;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "flowops.ai.enabled", havingValue = "true")
public class OllamaWorkModelAdapter implements LocalLanguageModelPort {
    private static final Logger LOG = LoggerFactory.getLogger(OllamaWorkModelAdapter.class);

    private static final String ANSWER_SHAPE =
            """
            {
              "type": "object",
              "properties": {
                "reasoning": { "type": "string" },
                "shape": { "type": "string", "enum": ["NOTHING", "TASK", "PROCESS"] },
                "stepPhrases": { "type": "array", "items": { "type": "string" } },
                "stepOwners": { "type": "array", "items": { "type": "string" } }
              },
              "required": ["reasoning", "shape", "stepPhrases", "stepOwners"]
            }
            """;

    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private final String root;
    private final String model;
    private final Duration patience;

    public OllamaWorkModelAdapter(
            @Value("${flowops.ai.ollama-url:http://localhost:11434}") String root,
            @Value("${flowops.ai.model:llama3.2:3b}") String model,
            @Value("${flowops.ai.timeout-seconds:45}") long timeoutSeconds) {
        this.root = refuseAnythingButLocal(root);
        this.model = model;
        this.patience = Duration.ofSeconds(timeoutSeconds);
        this.http =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    private static String refuseAnythingButLocal(String configured) {
        URI address = URI.create(configured);
        String host = address.getHost() == null ? "" : address.getHost();
        boolean local = host.equals("localhost")
                || host.equals("127.0.0.1")
                || host.equals("::1")
                || host.equals("host.docker.internal")
                || host.equals("flowops-ollama")
                || host.equals("ollama");
        if (!local) {
            throw new IllegalStateException("chat-assist may only reach a model inside this installation, and " + host
                    + " is not one. DECISION-CHAT-ASSIST-LOCAL-01 condition 2.");
        }
        return configured;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public Optional<WorkOpinion> readWorkIn(ConversationExtract conversation) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(root + "/api/generate"))
                    .timeout(patience)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body(conversation)))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                LOG.debug("the model answered {} rather than 200", response.statusCode());
                return Optional.empty();
            }
            return read(json.readTree(response.body()).path("response").asText());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception unreachable) {
            LOG.debug("the model could not be asked", unreachable);
            return Optional.empty();
        }
    }

    private String body(ConversationExtract conversation) throws Exception {
        StringBuilder lines = new StringBuilder();
        for (ConversationExtract.Line line : conversation.lines()) {
            lines.append(line.speaker()).append(": ").append(line.said()).append('\n');
        }

        String prompt =
                """
                Decide what a conversation describes. Answer with one of three shapes.

                NOTHING - no work at all. Arranging a meeting, confirming a date, agreeing
                somebody is in on Thursday, or ordinary chat. Most conversations are NOTHING.

                TASK - one person's job with several parts to it. The parts are things that
                person ticks off themselves, in order. Nobody hands anything over.

                PROCESS - work that changes hands. Different people own different parts, and
                one part must finish before the next person can start.

                The line between TASK and PROCESS is whether the work changes hands. Several
                parts alone does not make a process.

                If it is a TASK or a PROCESS, list the short phrases from the conversation that
                name the parts, in the order the work happens. For NOTHING, list nothing.

                For each part, also give the speaker who will do it, as their label - P1, P2.
                Use the speaker who said they would do it. If nobody said, give an empty string.
                stepOwners must have exactly as many entries as stepPhrases.

                Use only words that appear in the conversation. Do not invent step names, do
                not translate, and do not tidy the wording. Copy the phrases exactly.

                Conversation:
                %s
                """
                        .formatted(lines.toString().trim());

        ObjectNode request = json.createObjectNode();
        request.put("model", model);
        request.put("prompt", prompt);
        request.put("stream", false);
        request.set("format", json.readTree(ANSWER_SHAPE));
        request.set("options", json.createObjectNode().put("temperature", 0.1));
        return json.writeValueAsString(request);
    }

    private static WorkShape shapeOf(String said) {
        try {
            return WorkShape.valueOf(said.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException unrecognised) {
            return WorkShape.NOTHING;
        }
    }

    private static String blankToNull(String said) {
        return said == null || said.isBlank() ? null : said;
    }

    private Optional<WorkOpinion> read(String answer) {
        try {
            JsonNode said = json.readTree(answer);
            List<String> phrases = new ArrayList<>();
            for (JsonNode phrase : said.path("stepPhrases")) {
                phrases.add(phrase.asText());
            }
            List<String> owners = new ArrayList<>();
            for (JsonNode owner : said.path("stepOwners")) {
                owners.add(owner.asText());
            }

            boolean aligned = owners.size() == phrases.size();
            List<WorkOpinion.Part> parts = new ArrayList<>();
            for (int at = 0; at < phrases.size(); at++) {
                parts.add(new WorkOpinion.Part(phrases.get(at), aligned ? blankToNull(owners.get(at)) : null));
            }

            return Optional.of(new WorkOpinion(
                    shapeOf(said.path("shape").asText()),
                    parts,
                    said.path("reasoning").asText(null)));
        } catch (Exception unparseable) {
            LOG.debug("the model answered in a shape this build does not read", unparseable);
            return Optional.empty();
        }
    }
}
