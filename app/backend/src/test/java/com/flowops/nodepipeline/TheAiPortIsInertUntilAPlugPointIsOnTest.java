package com.flowops.nodepipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.nodepipeline.application.port.WorkJudgePort;
import com.flowops.nodepipeline.domain.NodeVerdict;
import com.flowops.nodepipeline.domain.PipelineNode;
import com.flowops.nodepipeline.domain.ai.Judgement;
import com.flowops.nodepipeline.domain.match.NodeMatcher;
import com.flowops.nodepipeline.infrastructure.model.NoWorkJudgeAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TheAiPortIsInertUntilAPlugPointIsOnTest {
    @Test
    void aModelThatIsAvailableButUnaskedChangesNoVerdictOnAnyCorpus() {
        for (String corpusName : List.of("brutal", "multichat-medium", "multichat-extreme", "messy-test")) {
            GoldenCorpus corpus = GoldenCorpus.load(corpusName);

            NodeMatcher withoutModel = new NodeMatcher(corpus.weights(), corpus.lexicons());

            WorkJudgePort availableButUnasked = new AlwaysConfident(false);
            NodeMatcher withModel = new NodeMatcher(
                    corpus.weights(), corpus.lexicons(), (node, template) -> opinionOf(availableButUnasked, node));

            List<String> differences = new ArrayList<>();
            for (PipelineNode node : corpus.nodes()) {
                NodeVerdict plain = withoutModel.match(node, corpus.templates());
                NodeVerdict judged = withModel.match(node, corpus.templates());

                if (!plain.equals(judged)) {
                    differences.add("%s: %s/%s became %s/%s"
                            .formatted(node.id(), plain.tier(), plain.why(), judged.tier(), judged.why()));
                }
            }

            assertThat(differences)
                    .as("%s: an unasked model must change nothing at all", corpusName)
                    .isEmpty();
        }
    }

    @Test
    void anAskedModelMovesVerdictsWhereTheBandIsPopulated() {
        GoldenCorpus corpus = GoldenCorpus.load("brutal");

        NodeMatcher withoutModel = new NodeMatcher(corpus.weights(), corpus.lexicons());
        WorkJudgePort asked = new AlwaysConfident(true);
        NodeMatcher withModel =
                new NodeMatcher(corpus.weights(), corpus.lexicons(), (node, template) -> opinionOf(asked, node));

        long moved = corpus.nodes().stream()
                .filter(node ->
                        !withoutModel.match(node, corpus.templates()).equals(withModel.match(node, corpus.templates())))
                .count();

        assertThat(moved)
                .as("an asked model must reach the uncertainty band on this corpus, or every COMPARE "
                        + "assertion elsewhere is about a model that never spoke")
                .isGreaterThan(0);
    }

    @Test
    void theStubWouldHaveAnsweredIfItHadBeenAsked() {
        WorkJudgePort enabled = new AlwaysConfident(true);

        assertThat(enabled.isEnabled(Judgement.PlugPoint.SAME_WORK)).isTrue();
        assertThat(enabled.judge(question()))
                .as("so the silence in the test above is the plug point being off, not a broken stub")
                .isPresent();
    }

    @Test
    void aPlugPointThatIsOffAnswersNothingEvenWhenTheModelIsThere() {
        WorkJudgePort off = new AlwaysConfident(false);

        assertThat(off.isAvailable()).as("the model is present").isTrue();
        assertThat(off.isEnabled(Judgement.PlugPoint.SAME_WORK)).isFalse();
        assertThat(off.judge(question())).isEmpty();
    }

    @Test
    void theDefaultAdapterIsHonestlyEmptyRatherThanPretending() {
        WorkJudgePort none = new NoWorkJudgeAdapter();

        assertThat(none.isAvailable()).isFalse();
        assertThat(none.judge(question())).isEmpty();
        assertThat(none.callsRemaining()).isZero();
        assertThat(none.modelId())
                .as("nothing answered, so nothing is attributed")
                .isNull();
    }

    @Test
    void anUnrecognisedVerdictIsNotAnAnswer() {
        assertThat(new Judgement.Verdict("SAME", 0.9, "same subject").asSameWork())
                .contains(Judgement.SameWork.SAME);
        assertThat(new Judgement.Verdict("PROBABLY", 0.9, "hedging").asSameWork())
                .as("05_AI.md: unrecognised output is a call failure and falls back to deterministic")
                .isEmpty();
    }

    private static Double opinionOf(WorkJudgePort port, PipelineNode node) {
        return port.judge(new Judgement.Question(Judgement.PlugPoint.SAME_WORK, node.text(), List.of("T-A"), null))
                .map(Judgement.Verdict::confidence)
                .orElse(null);
    }

    private static Judgement.Question question() {
        return new Judgement.Question(
                Judgement.PlugPoint.SAME_WORK, "brighten the header images", List.of("T-CROP"), null);
    }

    private record AlwaysConfident(boolean plugPointsOn) implements WorkJudgePort {
        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public boolean isEnabled(Judgement.PlugPoint plugPoint) {
            return plugPointsOn;
        }

        @Override
        public Optional<Judgement.Verdict> judge(Judgement.Question question) {
            if (!plugPointsOn) {
                return Optional.empty();
            }
            return Optional.of(new Judgement.Verdict("SAME", 0.95, "the words agree"));
        }

        @Override
        public int callsRemaining() {
            return 200;
        }

        @Override
        public String modelId() {
            return "llama3.2:3b";
        }

        @Override
        public String promptVersion() {
            return "v1";
        }
    }
}
