package com.flowops.nodepipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowops.nodepipeline.domain.CandidateTemplate;
import com.flowops.nodepipeline.domain.PipelineNode;
import com.flowops.nodepipeline.domain.match.Lexicons;
import com.flowops.nodepipeline.domain.match.MatchWeights;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

record GoldenCorpus(
        String name,
        MatchWeights weights,
        Lexicons lexicons,
        List<CandidateTemplate> templates,
        List<PipelineNode> nodes,
        Map<String, Expected> expected,

        /**
         * The template each node really belongs to, by node id, absent where the node is not work
         * any template describes. This is the corpus's own ground truth and is what precision and
         * recall have to be measured against -- {@code expected} is only what the simulator that
         * produced the fixture decided, so scoring against it would score the matcher on agreeing
         * with itself.
         */
        Map<String, String> truth) {
    record Expected(
            String tier, String why, String top, Double score, Double confidence, Double separation, Double text) {}

    private static final ObjectMapper JSON = new ObjectMapper();

    static GoldenCorpus load(String name) {
        try (InputStream stream = GoldenCorpus.class.getResourceAsStream("/nodepipeline/golden/" + name + ".json")) {
            if (stream == null) {
                throw new IllegalStateException("golden corpus " + name + " is missing; regenerate the fixtures");
            }
            JsonNode root = JSON.readTree(stream);
            return new GoldenCorpus(
                    name,
                    weightsFrom(root.get("config")),
                    lexiconsFrom(root),
                    templatesFrom(root.get("templates")),
                    nodesFrom(root.get("nodes")),
                    expectedFrom(root.get("expected")),
                    truthFrom(root.get("nodes")));
        } catch (IOException failure) {
            throw new IllegalStateException("golden corpus " + name + " could not be read", failure);
        }
    }

    private static MatchWeights weightsFrom(JsonNode config) {
        return new MatchWeights(
                config.get("W_TEXT").asDouble(),
                config.get("W_ROLE").asDouble(),
                config.get("W_SHAPE").asDouble(),
                config.get("W_OUTPUT").asDouble(),
                config.get("SCORE_FLOOR").asDouble(),
                config.get("CONF_FLOOR").asDouble(),
                config.get("SEP_MIN").asDouble(),
                config.get("TEXT_VETO").asDouble(),
                config.get("TQ_MIN").asDouble(),
                config.get("FINALISTS").asInt(),
                config.get("PLAUSIBLE_MIN").asDouble(),
                config.get("UNIQ_MIN").asDouble(),
                config.get("KW_CORROB").asDouble(),
                new MatchWeights.Gates(
                        config.get("boundary_gate").asBoolean(),
                        config.get("lapsed_gate").asBoolean(),
                        config.get("intent_gate").asBoolean(),
                        config.get("direction_gate").asBoolean(),
                        config.get("post_close_gate").asBoolean(),
                        config.get("shape_feature").asBoolean(),
                        config.get("output_map").asBoolean(),
                        config.get("keywords").asBoolean(),
                        config.get("type_contradiction").asBoolean(),
                        config.get("marker_address").asBoolean()));
    }

    private static Lexicons lexiconsFrom(JsonNode root) {
        Map<Lexicons.RolePair, Double> kinship = new LinkedHashMap<>();
        root.get("roleKinship")
                .forEach(pair -> kinship.put(
                        Lexicons.RolePair.of(pair.get(0).asText(), pair.get(1).asText()),
                        pair.get(2).asDouble()));

        Map<String, List<String>> typeWords = new LinkedHashMap<>();
        root.get("typeWords").fields().forEachRemaining(entry -> {
            List<String> words = new ArrayList<>();
            entry.getValue().forEach(word -> words.add(word.asText()));
            typeWords.put(entry.getKey(), words);
        });

        return new Lexicons(kinship, typeWords);
    }

    private static List<CandidateTemplate> templatesFrom(JsonNode array) {
        List<CandidateTemplate> templates = new ArrayList<>();
        array.forEach(t -> templates.add(new CandidateTemplate(
                text(t, "id"),
                text(t, "title"),
                text(t, "description"),
                text(t, "work_type"),
                text(t, "responsible_role"),
                text(t, "output_kind"),
                text(t, "expected_output"),
                text(t, "required_input"),
                text(t, "completion_criteria"),
                strings(t, "checklist"),
                text(t, "status"),
                date(t, "approved_at"),
                strings(t, "keywords"),
                text(t, "shape_preceding_role"),
                text(t, "shape_following_role"),
                integer(t, "shape_position"))));
        return templates;
    }

    private static List<PipelineNode> nodesFrom(JsonNode array) {
        List<PipelineNode> nodes = new ArrayList<>();
        array.forEach(n -> nodes.add(new PipelineNode(
                text(n, "id"),
                text(n, "job_id"),
                text(n, "text"),
                text(n, "detail"),
                person(text(n, "creator_id")),
                person(text(n, "marker_id")),
                text(n, "kind"),
                text(n, "creator_role_id"),
                text(n, "performer_role_id"),
                date(n, "created_at"),
                closure(text(n, "closure")),
                text(n, "direction"),
                bool(n, "post_close"),
                text(n, "output_type"),
                text(n, "work_type"),
                text(n, "task_template_id"),
                bool(n, "disrupted"),
                text(n, "conversation_id"),
                text(n, "shape_preceding_role"),
                text(n, "shape_preceding_dir"),
                text(n, "shape_following_role"),
                integer(n, "shape_position"))));
        return nodes;
    }

    private static Map<String, String> truthFrom(JsonNode array) {
        Map<String, String> truth = new LinkedHashMap<>();
        array.forEach(node -> {
            JsonNode label = node.get("truth");
            if (label != null && !label.isNull()) {
                truth.put(node.get("id").asText(), label.asText());
            }
        });
        return truth;
    }

    private static Map<String, Expected> expectedFrom(JsonNode array) {
        Map<String, Expected> expected = new LinkedHashMap<>();
        array.forEach(e -> expected.put(
                e.get("id").asText(),
                new Expected(
                        text(e, "tier"),
                        text(e, "why"),
                        text(e, "top"),
                        decimal(e, "score"),
                        decimal(e, "conf"),
                        decimal(e, "sep"),
                        decimal(e, "txt"))));
        return expected;
    }

    private static PipelineNode.Closure closure(String value) {
        return value == null ? null : PipelineNode.Closure.valueOf(value);
    }

    private static UUID person(String name) {
        return name == null ? null : UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static Double decimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asDouble();
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asInt();
    }

    private static boolean bool(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && !value.isNull() && value.asBoolean();
    }

    private static LocalDate date(JsonNode node, String field) {
        String value = text(node, field);
        return value == null ? null : LocalDate.parse(value);
    }

    private static List<String> strings(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        value.forEach(item -> items.add(item.asText()));
        return items;
    }
}
