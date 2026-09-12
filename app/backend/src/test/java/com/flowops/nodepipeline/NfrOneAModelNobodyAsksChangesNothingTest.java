package com.flowops.nodepipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.nodepipeline.domain.NodeVerdict;
import com.flowops.nodepipeline.domain.PipelineNode;
import com.flowops.nodepipeline.domain.RunSignature;
import com.flowops.nodepipeline.domain.ai.Judgement;
import com.flowops.nodepipeline.domain.match.MatchWeights;
import com.flowops.nodepipeline.domain.match.NodeMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * NFR-1, tested rather than asserted in prose.
 *
 * <p>The claim is that a run with the model <em>available</em> but every plug point <em>disabled</em>
 * produces output identical to a run with no model configured at all. It is two claims, and they do
 * not have the same answer: every decision is identical, and the run signature is not, because
 * {@link RunSignature} records the model's identity whether or not anybody asked it anything.
 */
class NfrOneAModelNobodyAsksChangesNothingTest {

    /** Present, answering, and switched off at every plug point — the NFR-1 configuration. */
    private static final class AvailableButUnasked implements NodeMatcher.WorkJudge {
        private int askedTimes;

        @Override
        public boolean isEnabled(Judgement.PlugPoint plugPoint) {
            return false;
        }

        @Override
        public Optional<Judgement.Verdict> judge(Judgement.Question question) {
            askedTimes++;
            return Optional.of(new Judgement.Verdict("SAME", 0.99, "would have spoken"));
        }
    }

    private static List<NodeVerdict> runOver(GoldenCorpus corpus, NodeMatcher matcher) {
        List<NodeVerdict> verdicts = new ArrayList<>();
        for (PipelineNode node : corpus.nodes()) {
            verdicts.add(matcher.match(node, corpus.templates()));
        }
        return verdicts;
    }

    @Test
    void everyDecisionIsIdenticalWhenNoPlugPointIsOn() {
        GoldenCorpus corpus = GoldenCorpus.load("brutal");
        MatchWeights weights = MatchWeights.reference();

        List<NodeVerdict> withNoModelAtAll = runOver(corpus, new NodeMatcher(weights, corpus.lexicons()));

        AvailableButUnasked present = new AvailableButUnasked();
        List<NodeVerdict> withAModelNobodyAsks = runOver(corpus, new NodeMatcher(weights, corpus.lexicons(), present));

        assertThat(withAModelNobodyAsks)
                .as("123 nodes, and not one of them decided differently because a model was in the room")
                .isEqualTo(withNoModelAtAll);

        assertThat(present.askedTimes)
                .as("and it was never asked, which is the mechanism rather than a coincidence of the answers")
                .isZero();
    }

    @Test
    void theRunSignatureIsNotIdenticalWhichIsWhereTheNfrAsWordedFails() {
        MatchWeights weights = MatchWeights.reference();
        GoldenCorpus corpus = GoldenCorpus.load("brutal");

        String withoutAModel = RunSignature.of(weights, corpus.lexicons(), "OFF", null, null);
        String withOneConfigured = RunSignature.of(weights, corpus.lexicons(), "OFF", "llama3.2:3b", "v1");

        assertThat(withOneConfigured)
                .as("RunSignature records ai.model and ai.prompt whether or not a plug point is on, so "
                        + "two runs that decided identically are signed differently. The decisions are "
                        + "the guarantee NFR-1 actually delivers; 'byte-identical output' overstates it.")
                .isNotEqualTo(withoutAModel);
    }
}
