package com.flowops.nodepipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.nodepipeline.domain.NodeVerdict;
import com.flowops.nodepipeline.domain.PipelineNode;
import com.flowops.nodepipeline.domain.ai.Judgement;
import com.flowops.nodepipeline.domain.match.NodeMatcher;
import com.flowops.nodepipeline.infrastructure.model.OllamaWorkJudgeAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("NODEPIPE-AI-01")
@Tag("livemodel")
class TheModelAnswersAndStaysInertTest {
    private static final String OLLAMA = "http://localhost:11434";
    private static final String MODEL = "llama3.2:3b";
    private static final int TIMEOUT_SECONDS = 30;
    private static final int BUDGET = 200;

    @Test
    void theModelAnswersABoundedQuestionInTheShapeTheValidatorAccepts() {
        OllamaWorkJudgeAdapter asked = adapterWith(false, true, false);

        Optional<Judgement.Verdict> verdict = asked.judge(new Judgement.Question(
                Judgement.PlugPoint.SAME_KIND,
                "write the launch copy for the Aurora campaign",
                List.of("K-WRITER-TEXT"),
                "drafting words for a client launch"));

        assertThat(verdict)
                .as("a live llama3.2:3b must answer a closed question, or nothing below means anything")
                .isPresent();
        assertThat(verdict.get().value()).isNotBlank();
        assertThat(verdict.get().confidence())
                .as("the validator rejects anything outside 0..1, so a present verdict is already in range")
                .isBetween(0.0, 1.0);
        assertThat(asked.callsRemaining()).as("exactly one question was asked").isEqualTo(BUDGET - 1);
    }

    @Test
    void everyPlugPointIsSilentWhenOffAndNoCallIsMade() {
        OllamaWorkJudgeAdapter off = adapterWith(false, false, false);

        assertThat(off.isAvailable())
                .as("the model is present and would answer")
                .isTrue();

        for (Judgement.PlugPoint plugPoint : Judgement.PlugPoint.values()) {
            assertThat(off.isEnabled(plugPoint)).isFalse();
            assertThat(off.judge(
                            new Judgement.Question(plugPoint, "brighten the header images", List.of("T-CROP"), null)))
                    .as("%s answered while switched off", plugPoint)
                    .isEmpty();
        }

        assertThat(off.callsRemaining())
                .as("not one request reached the daemon")
                .isEqualTo(BUDGET);
    }

    @Test
    void aLiveModelWithEveryPlugPointOffChangesNoVerdictOnAnyCorpus() {
        OllamaWorkJudgeAdapter off = adapterWith(false, false, false);

        for (String corpusName : List.of("brutal", "multichat-medium", "multichat-extreme", "messy-test")) {
            GoldenCorpus corpus = GoldenCorpus.load(corpusName);

            NodeMatcher deterministic = new NodeMatcher(corpus.weights(), corpus.lexicons());
            NodeMatcher withLiveModel = new NodeMatcher(
                    corpus.weights(), corpus.lexicons(), (node, template) -> off.judge(new Judgement.Question(
                                    Judgement.PlugPoint.SAME_WORK, node.text(), List.of(template.id()), null))
                            .map(Judgement.Verdict::confidence)
                            .orElse(null));

            List<String> differences = new ArrayList<>();
            for (PipelineNode node : corpus.nodes()) {
                NodeVerdict plain = deterministic.match(node, corpus.templates());
                NodeVerdict judged = withLiveModel.match(node, corpus.templates());
                if (!plain.equals(judged)) {
                    differences.add("%s: %s/%s became %s/%s"
                            .formatted(node.id(), plain.tier(), plain.why(), judged.tier(), judged.why()));
                }
            }

            assertThat(differences)
                    .as("%s: a live model nobody asked must change nothing at all", corpusName)
                    .isEmpty();
        }

        assertThat(off.callsRemaining())
                .as("461 nodes and not one call -- the gate is in front of the request")
                .isEqualTo(BUDGET);
    }

    private static OllamaWorkJudgeAdapter adapterWith(boolean sameWork, boolean sameKind, boolean naming) {
        return new OllamaWorkJudgeAdapter(
                OLLAMA,
                MODEL,
                TIMEOUT_SECONDS,
                BUDGET,
                sameWork,
                sameKind,
                naming,
                false,
                new com.flowops.nodepipeline.application.AiSwitch());
    }
}
