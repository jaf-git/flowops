package com.flowops.nodepipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.nodepipeline.domain.NodeVerdict;
import com.flowops.nodepipeline.domain.PipelineNode;
import com.flowops.nodepipeline.domain.match.NodeMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NodeMatcherMatchesTheOracleTest {
    private static final double TOLERANCE = 0.0005;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"brutal", "multichat-medium", "multichat-extreme", "messy-test"})
    void everyNodeReachesTheVerdictTheOracleReached(String corpusName) {
        GoldenCorpus corpus = GoldenCorpus.load(corpusName);
        NodeMatcher matcher = new NodeMatcher(corpus.weights(), corpus.lexicons());

        List<String> disagreements = new ArrayList<>();

        for (PipelineNode node : corpus.nodes()) {
            GoldenCorpus.Expected expected = corpus.expected().get(node.id());
            assertThat(expected)
                    .as("the fixture must carry a verdict for every node it carries")
                    .isNotNull();

            NodeVerdict actual = matcher.match(node, corpus.templates());

            if (!expected.tier().equals(actual.tier().name())) {
                disagreements.add("%s tier: oracle %s, java %s (%s / %s)"
                        .formatted(node.id(), expected.tier(), actual.tier(), expected.why(), actual.why()));
                continue;
            }
            if (!java.util.Objects.equals(expected.why(), actual.why())) {
                disagreements.add("%s why: oracle '%s', java '%s'".formatted(node.id(), expected.why(), actual.why()));
            }
            if (!java.util.Objects.equals(expected.top(), actual.topTemplateId())) {
                disagreements.add(
                        "%s top: oracle %s, java %s".formatted(node.id(), expected.top(), actual.topTemplateId()));
            }
            disagree(disagreements, node.id(), "score", expected.score(), actual.score());
            disagree(disagreements, node.id(), "conf", expected.confidence(), actual.confidence());
            disagree(disagreements, node.id(), "sep", expected.separation(), actual.separation());
            disagree(disagreements, node.id(), "txt", expected.text(), actual.textScore());
        }

        assertThat(disagreements)
                .as(
                        "%s: %d nodes, and Java must reach the oracle's verdict on each",
                        corpusName, corpus.nodes().size())
                .isEmpty();
    }

    @Test
    void theShapeOfTheAnswerIsStillMostlySilence() {
        Map<String, Map<String, Integer>> byCorpus = new TreeMap<>();

        for (String name : List.of("brutal", "multichat-medium", "multichat-extreme", "messy-test")) {
            GoldenCorpus corpus = GoldenCorpus.load(name);
            NodeMatcher matcher = new NodeMatcher(corpus.weights(), corpus.lexicons());

            Map<String, Integer> tiers = new TreeMap<>();
            for (PipelineNode node : corpus.nodes()) {
                tiers.merge(matcher.match(node, corpus.templates()).tier().name(), 1, Integer::sum);
            }
            byCorpus.put(name, tiers);
        }

        assertThat(byCorpus.get("brutal")).containsExactlyInAnyOrderEntriesOf(Map.of("ABSTAIN", 110, "NUDGE", 13));
        assertThat(byCorpus.get("multichat-medium"))
                .containsExactlyInAnyOrderEntriesOf(Map.of("ABSTAIN", 52, "NUDGE", 6));
        assertThat(byCorpus.get("multichat-extreme"))
                .containsExactlyInAnyOrderEntriesOf(Map.of("ABSTAIN", 141, "NUDGE", 29));
        assertThat(byCorpus.get("messy-test")).containsExactlyInAnyOrderEntriesOf(Map.of("ABSTAIN", 35, "NUDGE", 75));
    }

    @Test
    void noCorpusExceedsThePhasesVolumeGuard() {
        for (String name : List.of("brutal", "multichat-medium", "multichat-extreme", "messy-test")) {
            assertThat(GoldenCorpus.load(name).nodes().size())
                    .as("%s is within P3's 200-node run guard", name)
                    .isLessThanOrEqualTo(200);
        }
    }

    private static void disagree(List<String> into, String id, String field, Double oracle, Double java) {
        if (oracle == null && java == null) {
            return;
        }
        if (oracle == null || java == null || Math.abs(oracle - java) > TOLERANCE) {
            into.add("%s %s: oracle %s, java %s".formatted(id, field, oracle, java));
        }
    }
}
