package com.flowops.nodepipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.nodepipeline.domain.MatchTier;
import com.flowops.nodepipeline.domain.NodeVerdict;
import com.flowops.nodepipeline.domain.PipelineNode;
import com.flowops.nodepipeline.domain.ai.Judgement;
import com.flowops.nodepipeline.domain.match.NodeMatcher;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("NODEPIPE-AI-01")
class TheModelMayLowerATierAndOnlyRaiseAPermittedOneTest {
    @Test
    void aConfidentSameLiftsAnAbstentionThatFailedOnScoreAlone() {
        NodeVerdict verdict = matchWith(new AlwaysSays("SAME"), aNodeInTheUncertaintyBand());

        assertThat(verdict.tier())
                .as("below_floor plus a confident SAME is the one promotion the gate permits")
                .isEqualTo(MatchTier.NUDGE);
        assertThat(verdict.why()).isEqualTo("ai_promoted");
    }

    @Test
    void aModelSayingDifferentNeverPromotesAnything() {
        NodeVerdict verdict = matchWith(new AlwaysSays("DIFFERENT"), aNodeInTheUncertaintyBand());

        assertThat(verdict.tier()).isEqualTo(MatchTier.ABSTAIN);
        assertThat(verdict.why())
                .as("nothing changed, so the reason must still be the deterministic one")
                .isEqualTo("below_floor");
    }

    @Test
    void anUnsureModelLeavesTheAbstentionAlone() {
        NodeVerdict verdict = matchWith(new AlwaysSays("UNSURE"), aNodeInTheUncertaintyBand());

        assertThat(verdict.tier()).isEqualTo(MatchTier.ABSTAIN);
        assertThat(verdict.why()).isEqualTo("below_floor");
    }

    @Test
    void aTextVetoIsNotSomethingAModelMayOverturn() {
        NodeVerdict verdict = matchWith(new AlwaysSays("SAME"), aNodeWhoseWordsDisagree());

        assertThat(verdict.tier())
                .as("the words disagree; ADR-008 makes that absolute and a model does not outrank it")
                .isEqualTo(MatchTier.ABSTAIN);
        assertThat(verdict.why())
                .as("the reason must still name the veto, not the model")
                .isEqualTo("veto:text_floor");
    }

    @Test
    void aPlugPointThatIsOffLeavesEveryVerdictAlone() {
        CountingJudge off = new CountingJudge(false);

        NodeVerdict verdict = matchWith(off, aNodeInTheUncertaintyBand());

        assertThat(off.asked)
                .as("no question may be asked when the plug point is off")
                .isZero();
        assertThat(verdict.why()).isNotEqualTo("ai_vetoed").isNotEqualTo("ai_promoted");
    }

    @Test
    void aConfidentMatchIsNeverAskedAbout() {
        CountingJudge on = new CountingJudge(true);

        matchWith(on, aConfidentlyMatchedNode());

        assertThat(on.asked)
                .as("outside the uncertainty band, a call is spent to be told what is already known")
                .isZero();
    }

    private static NodeVerdict matchWith(NodeMatcher.WorkJudge judge, PipelineNode node) {
        GoldenCorpus corpus = GoldenCorpus.load("brutal");
        return new NodeMatcher(corpus.weights(), corpus.lexicons(), judge).match(node, corpus.templates());
    }

    private static PipelineNode aNodeInTheUncertaintyBand() {
        return aNodeWhoseDeterministicReasonIs("below_floor");
    }

    private static PipelineNode aConfidentlyMatchedNode() {
        GoldenCorpus corpus = GoldenCorpus.load("brutal");
        NodeMatcher deterministic = new NodeMatcher(corpus.weights(), corpus.lexicons());
        return corpus.nodes().stream()
                .filter(node -> {
                    NodeVerdict plain = deterministic.match(node, corpus.templates());
                    return plain.tier() == MatchTier.NUDGE || plain.tier() == MatchTier.OK;
                })
                .findFirst()
                .orElseThrow(() -> new AssertionError("the brutal corpus has no confidently matched node"));
    }

    private static PipelineNode aNodeWhoseDeterministicReasonIs(String reason) {
        GoldenCorpus corpus = GoldenCorpus.load("brutal");
        NodeMatcher deterministic = new NodeMatcher(corpus.weights(), corpus.lexicons());
        return corpus.nodes().stream()
                .filter(node -> reason.equals(
                        deterministic.match(node, corpus.templates()).why()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the brutal corpus has no node whose reason is " + reason));
    }

    private static PipelineNode aNodeWhoseWordsDisagree() {
        return aNodeWhoseDeterministicReasonIs("veto:text_floor");
    }

    private record AlwaysSays(String value) implements NodeMatcher.WorkJudge {
        @Override
        public boolean isEnabled(Judgement.PlugPoint plugPoint) {
            return true;
        }

        @Override
        public Optional<Judgement.Verdict> judge(Judgement.Question question) {
            return Optional.of(new Judgement.Verdict(value, 0.9, "because"));
        }
    }

    private static final class CountingJudge implements NodeMatcher.WorkJudge {
        private final boolean on;
        private int asked;

        CountingJudge(boolean on) {
            this.on = on;
        }

        @Override
        public boolean isEnabled(Judgement.PlugPoint plugPoint) {
            return on;
        }

        @Override
        public Optional<Judgement.Verdict> judge(Judgement.Question question) {
            asked++;
            return Optional.of(new Judgement.Verdict("SAME", 0.9, "because"));
        }
    }
}
