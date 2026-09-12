package com.flowops.nodepipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.nodepipeline.domain.PipelineNode;
import com.flowops.nodepipeline.domain.ai.ConceptSplit;
import com.flowops.nodepipeline.domain.ai.Judgement;
import com.flowops.nodepipeline.infrastructure.model.OllamaWorkJudgeAdapter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("NODEPIPE-AI-01")
@Tag("livemodel")
class IsTheModelReproducibleTest {
    private static final int SAMPLE = 12;

    @Test
    void twoColdRunsAndTwoWarmOnesOverTheSameNodes() throws Exception {
        DiscoveryFixture corpus = DiscoveryFixture.load("multichat-extreme");
        List<PipelineNode> sample = corpus.nodes().stream()
                .filter(node -> !ConceptSplit.conceptsFor(node.workType()).isEmpty())
                .limit(SAMPLE)
                .toList();

        OllamaWorkJudgeAdapter first = adapter();
        Map<String, String> coldOne = labels(first, sample);
        Map<String, String> warmOne = labels(first, sample);

        OllamaWorkJudgeAdapter second = adapter();
        Map<String, String> coldTwo = labels(second, sample);
        Map<String, String> warmTwo = labels(second, sample);

        int coldAgreement = agreementBetween(coldOne, coldTwo);
        System.out.printf(
                "%n=== REPRODUCIBILITY, %d nodes, llama3.2:3b, temperature 0 ===%n"
                        + "  cold vs cold : %d of %d agree%n"
                        + "  warm vs cold : %d of %d agree (same instance)%n"
                        + "  calls used   : %d then %d%n"
                        + "  VERDICT      : %s%n%n",
                sample.size(),
                coldAgreement,
                sample.size(),
                agreementBetween(coldOne, warmOne),
                sample.size(),
                400 - first.callsRemaining(),
                400 - second.callsRemaining(),
                coldAgreement == sample.size()
                        ? "deterministic across processes -- the cache is an optimisation"
                        : "NOT deterministic -- the cache is load-bearing, and 05_AI.md needs to say so");

        assertThat(warmOne)
                .as("a warm cache must return exactly what it stored, or it is not a cache")
                .isEqualTo(coldOne);
        assertThat(warmTwo).isEqualTo(coldTwo);

        assertThat(first.callsRemaining())
                .as("the warm pass must have cost nothing, or the cache key is wrong")
                .isEqualTo(400 - sample.size());
    }

    private static int agreementBetween(Map<String, String> one, Map<String, String> other) {
        int agreed = 0;
        for (Map.Entry<String, String> entry : one.entrySet()) {
            if (java.util.Objects.equals(entry.getValue(), other.get(entry.getKey()))) {
                agreed++;
            }
        }
        return agreed;
    }

    private static Map<String, String> labels(OllamaWorkJudgeAdapter model, List<PipelineNode> nodes) {
        Map<String, String> said = new LinkedHashMap<>();
        for (PipelineNode node : nodes) {
            Set<String> allowed = ConceptSplit.conceptsFor(node.workType());
            said.put(
                    node.id(),
                    model.judge(new Judgement.Question(
                                    Judgement.PlugPoint.SAME_KIND, node.text(), List.of(), String.join(", ", allowed)))
                            .map(Judgement.Verdict::value)
                            .orElse(null));
        }
        return said;
    }

    private static OllamaWorkJudgeAdapter adapter() {
        return new OllamaWorkJudgeAdapter(
                "http://localhost:11434",
                "llama3.2:3b",
                30,
                400,
                false,
                true,
                false,
                false,
                new com.flowops.nodepipeline.application.AiSwitch());
    }
}
