package com.flowops.aiassist.infrastructure.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowops.aiassist.application.port.LanguageModelPort;
import com.flowops.aiassist.domain.EvidencePacket;
import com.flowops.aiassist.domain.ShapeOpinion;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "flowops.ai.enabled", havingValue = "true")
public class OllamaLanguageModelAdapter implements LanguageModelPort {
    private static final Logger LOG = LoggerFactory.getLogger(OllamaLanguageModelAdapter.class);

    private static final String ANSWER_SHAPE =
            """
            {
              "type": "object",
              "properties": {
                "reasoning": { "type": "string" },
                "looksLikeAProcess": { "type": "boolean" },
                "stepKeys": { "type": "array", "items": { "type": "string" } }
              },
              "required": ["reasoning", "looksLikeAProcess", "stepKeys"]
            }
            """;

    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private final String root;
    private final String model;
    private final Duration patience;

    public OllamaLanguageModelAdapter(
            @Value("${flowops.ai.ollama-url:http://localhost:11434}") String root,
            @Value("${flowops.ai.model:llama3.2:3b}") String model,
            @Value("${flowops.ai.timeout-seconds:45}") long timeoutSeconds) {
        this.root = root;
        this.model = model;
        this.patience = Duration.ofSeconds(timeoutSeconds);
        this.http =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public Optional<ShapeOpinion> readShapeOf(EvidencePacket evidence) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(root + "/api/generate"))
                    .timeout(patience)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body(evidence)))
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

    private String body(EvidencePacket evidence) throws Exception {
        StringBuilder lines = new StringBuilder();
        for (EvidencePacket.Line line : evidence.lines()) {
            lines.append(line.key()).append(": ").append(line.text()).append('\n');
        }

        String prompt =
                """
                Decide whether a task template describes a PROCESS or a single TASK.

                A PROCESS is work that changes hands: different people own different parts,
                and one person's part must finish before the next person can start.

                A TASK is work one person does from start to finish, even when it has several
                steps. A checklist of things one person ticks off in order is a TASK, not a
                process.

                Title: %s
                %s
                Checklist items, each with a key:
                %s
                First write "reasoning": one short sentence naming whether the work changes
                hands between different people, or stays with one person.
                Then set "looksLikeAProcess" accordingly, and write "stepKeys": if it is a
                process, the keys of the checklist items that are steps, in the order they run,
                for example ["c1","c2"]. If it is a task, set looksLikeAProcess false and
                stepKeys to [].
                """
                        .formatted(
                                evidence.subject(),
                                evidence.context()
                                        .map(it -> "Description: " + it + System.lineSeparator())
                                        .orElse(""),
                                lines);

        com.fasterxml.jackson.databind.node.ObjectNode request = json.createObjectNode();
        request.put("model", model);
        request.put("stream", false);
        request.put("prompt", prompt);
        request.set("format", json.readTree(ANSWER_SHAPE));
        request.set("options", json.createObjectNode().put("temperature", 0.1).put("num_predict", 512));
        return json.writeValueAsString(request);
    }

    private Optional<ShapeOpinion> read(String answer) {
        if (answer == null || answer.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode parsed = json.readTree(answer);
            List<String> keys = new ArrayList<>();
            for (JsonNode key : parsed.path("stepKeys")) {
                String text = key.asText(null);
                if (text != null && !text.isBlank()) {
                    keys.add(text.trim());
                }
            }
            return Optional.of(new ShapeOpinion(
                    parsed.path("looksLikeAProcess").asBoolean(false),
                    keys,
                    parsed.path("reasoning").asText("")));
        } catch (Exception notJson) {
            LOG.debug("the model's answer did not parse", notJson);
            return Optional.empty();
        }
    }
}
