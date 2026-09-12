package com.flowops.nodepipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.nodepipeline.domain.CandidateTemplate;
import com.flowops.nodepipeline.domain.MatchTier;
import com.flowops.nodepipeline.domain.NodeVerdict;
import com.flowops.nodepipeline.domain.PipelineNode;
import com.flowops.nodepipeline.domain.ai.Judgement;
import com.flowops.nodepipeline.domain.match.MatchWeights;
import com.flowops.nodepipeline.domain.match.NodeMatcher;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * What AP-1 is actually worth, measured against the corpus's own ground truth rather than against
 * the simulator that produced the fixture.
 *
 * <p>Chapter 6 reports "+0.699 recall" for the {@code SAME_WORK} plug point and "22% of scored
 * nodes" for the uncertainty band it fires in. Neither had ever been computed from this codebase:
 * no test reads {@code truth}, and "keyword coverage" appears in no Java file. This measures both
 * from the shipped {@link NodeMatcher}, so the thesis can cite the product rather than the Python
 * simulator named in the fixture's own {@code producedBy} field.
 *
 * <p>The numbers are printed rather than asserted, because pinning a measurement in an assertion
 * turns a finding into a fixture nobody may improve. What <em>is</em> asserted is the structural
 * claim the thesis makes: the model is asked only inside the band, and it can change nothing
 * outside it.
 */
class WhatTheModelIsWorthOnTheBrutalCorpusTest {

    /** Precision and recall over the nodes a template genuinely describes. */
    private record Score(int truePositives, int falsePositives, int falseNegatives) {
        double precision() {
            int predicted = truePositives + falsePositives;
            return predicted == 0 ? 0.0 : (double) truePositives / predicted;
        }

        double recall() {
            int real = truePositives + falseNegatives;
            return real == 0 ? 0.0 : (double) truePositives / real;
        }

        double f1() {
            double p = precision();
            double r = recall();
            return p + r == 0.0 ? 0.0 : 2 * p * r / (p + r);
        }

        @Override
        public String toString() {
            return "TP %d  FP %d  FN %d  |  precision %.3f  recall %.3f  F1 %.3f"
                    .formatted(truePositives, falsePositives, falseNegatives, precision(), recall(), f1());
        }
    }

    private static Score scoreOf(List<NodeVerdict> verdicts, Map<String, String> truth) {
        int tp = 0;
        int fp = 0;
        int fn = 0;

        Set<String> answered = new LinkedHashSet<>();
        for (NodeVerdict verdict : verdicts) {
            answered.add(verdict.nodeId());
            String correct = truth.get(verdict.nodeId());

            if (!verdict.acts()) {
                if (correct != null) {
                    fn++;
                }
                continue;
            }
            if (correct != null && correct.equals(verdict.topTemplateId())) {
                tp++;
            } else {
                fp++;
                if (correct != null) {
                    fn++;
                }
            }
        }

        // a node the matcher never reached is a miss, not an absence
        for (String labelled : truth.keySet()) {
            if (!answered.contains(labelled)) {
                fn++;
            }
        }
        return new Score(tp, fp, fn);
    }

    /** Says SAME, confidently, every single time it is asked — AP-1's best possible day. */
    private static final class AlwaysSame implements NodeMatcher.WorkJudge {
        private final List<String> asked = new ArrayList<>();

        @Override
        public boolean isEnabled(Judgement.PlugPoint plugPoint) {
            return plugPoint == Judgement.PlugPoint.SAME_WORK;
        }

        @Override
        public Optional<Judgement.Verdict> judge(Judgement.Question question) {
            asked.add(question.text());
            return Optional.of(new Judgement.Verdict("SAME", 0.95, "the words agree"));
        }
    }

    private static List<CandidateTemplate> withoutKeywords(List<CandidateTemplate> templates) {
        List<CandidateTemplate> stripped = new ArrayList<>();
        for (CandidateTemplate t : templates) {
            stripped.add(new CandidateTemplate(
                    t.id(),
                    t.title(),
                    t.description(),
                    t.workType(),
                    t.responsibleRole(),
                    t.outputKind(),
                    t.expectedOutput(),
                    t.requiredInput(),
                    t.completionCriteria(),
                    t.checklist(),
                    t.status(),
                    t.approvedAt(),
                    List.of(),
                    t.precedingRole(),
                    t.followingRole(),
                    t.position()));
        }
        return stripped;
    }

    private static List<NodeVerdict> runOver(
            GoldenCorpus corpus, List<CandidateTemplate> library, NodeMatcher.WorkJudge judge) {
        MatchWeights weights = MatchWeights.reference();
        NodeMatcher matcher = judge == null
                ? new NodeMatcher(weights, corpus.lexicons())
                : new NodeMatcher(weights, corpus.lexicons(), judge);

        List<NodeVerdict> verdicts = new ArrayList<>();
        for (PipelineNode node : corpus.nodes()) {
            verdicts.add(matcher.match(node, library));
        }
        return verdicts;
    }

    private static boolean inBand(NodeVerdict verdict, MatchWeights weights) {
        return verdict.textScore() != null
                && verdict.score() != null
                && verdict.textScore() >= weights.textVeto()
                && verdict.score() < weights.scoreFloor();
    }

    @Test
    void theModelIsAskedOnlyInsideTheBandAndChangesNothingOutsideIt() {
        GoldenCorpus corpus = GoldenCorpus.load("brutal");
        MatchWeights weights = MatchWeights.reference();

        List<NodeVerdict> deterministic = runOver(corpus, corpus.templates(), null);

        AlwaysSame model = new AlwaysSame();
        List<NodeVerdict> withModel = runOver(corpus, corpus.templates(), model);

        List<NodeVerdict> band =
                deterministic.stream().filter(v -> inBand(v, weights)).toList();

        assertThat(model.asked)
                .as(
                        "AP-1 fires only between the text veto (%.2f) and the score floor (%.2f); the "
                                + "confident nodes and the vetoed ones never call",
                        weights.textVeto(), weights.scoreFloor())
                .hasSize(band.size());

        for (int at = 0; at < deterministic.size(); at++) {
            NodeVerdict before = deterministic.get(at);
            NodeVerdict after = withModel.get(at);
            if (!inBand(before, weights)) {
                assertThat(after.tier())
                        .as("node %s is outside the band, so no model may move it", before.nodeId())
                        .isEqualTo(before.tier());
            }
        }
    }

    @Test
    void whatAp1IsWorthOnItsBestPossibleDay() {
        GoldenCorpus corpus = GoldenCorpus.load("brutal");
        MatchWeights weights = MatchWeights.reference();
        Map<String, String> truth = corpus.truth();

        StringBuilder report = new StringBuilder(
                "\n=== AP-1 on the brutal corpus, %d nodes, %d of them work a template really describes ===\n"
                        .formatted(corpus.nodes().size(), truth.size()));

        for (boolean keywords : new boolean[] {true, false}) {
            List<CandidateTemplate> library = keywords ? corpus.templates() : withoutKeywords(corpus.templates());

            List<NodeVerdict> off = runOver(corpus, library, null);
            List<NodeVerdict> on = runOver(corpus, library, new AlwaysSame());

            long scored = off.stream().filter(v -> v.score() != null).count();
            long band = off.stream().filter(v -> inBand(v, weights)).count();

            report.append("\n-- keyword coverage: %s\n".formatted(keywords ? "as authored" : "ZERO"))
                    .append("   scored %d of %d, band %d (%.1f%% of scored)\n"
                            .formatted(scored, off.size(), band, scored == 0 ? 0.0 : 100.0 * band / scored))
                    .append("   AP-1 off : %s\n".formatted(scoreOf(off, truth)))
                    .append("   AP-1 on  : %s\n".formatted(scoreOf(on, truth)))
                    .append("   recall change %+.3f, precision change %+.3f\n"
                            .formatted(
                                    scoreOf(on, truth).recall()
                                            - scoreOf(off, truth).recall(),
                                    scoreOf(on, truth).precision()
                                            - scoreOf(off, truth).precision()));
        }

        System.out.println(report);

        // The corpus has to be able to answer the question at all, or the numbers above are noise.
        assertThat(truth).as("the corpus carries ground truth").isNotEmpty();
        assertThat(corpus.nodes()).hasSize(123);
    }

    @Test
    void theSimulatorThatProducedTheFixtureAndTheShippedMatcherStillAgree() {
        GoldenCorpus corpus = GoldenCorpus.load("brutal");
        List<NodeVerdict> ours = runOver(corpus, corpus.templates(), null);

        List<String> disagreements = new ArrayList<>();
        for (NodeVerdict verdict : ours) {
            GoldenCorpus.Expected said = corpus.expected().get(verdict.nodeId());
            if (said == null) {
                continue;
            }
            if (!said.tier().equals(verdict.tier().name())) {
                disagreements.add("%s: fixture %s, ours %s".formatted(verdict.nodeId(), said.tier(), verdict.tier()));
            }
        }

        assertThat(disagreements)
                .as("the fixture was produced by sim/v6.py; where Java and the simulator disagree, "
                        + "the thesis cannot quote the simulator's numbers as the product's")
                .isEmpty();
    }

    @Test
    void aVerdictTheModelMovedSaysSoOnItsOwnRow() {
        GoldenCorpus corpus = GoldenCorpus.load("brutal");

        List<NodeVerdict> withModel = runOver(corpus, corpus.templates(), new AlwaysSame());

        List<NodeVerdict> promoted =
                withModel.stream().filter(v -> "ai_promoted".equals(v.why())).toList();

        assertThat(promoted)
                .as("the stub says SAME to everything in the band, so something must have moved")
                .isNotEmpty();

        // The regression this guards: a run could change a tier on a model's word and record
        // nothing but the string `ai_promoted`, which names no model and carries no confidence.
        assertThat(promoted).allSatisfy(verdict -> {
            assertThat(verdict.aiOutcome()).isEqualTo(verdict.tier().name());
            assertThat(verdict.aiConfidence()).isEqualTo(0.95);
        });

        assertThat(withModel.stream().filter(v -> !"ai_promoted".equals(v.why())))
                .as("and a decision the model did not move claims nothing")
                .allSatisfy(verdict -> {
                    assertThat(verdict.aiOutcome()).isNull();
                    assertThat(verdict.aiConfidence()).isNull();
                });
    }

    @Test
    void aModelSayingDifferentCanOnlyEverLowerATier() {
        GoldenCorpus corpus = GoldenCorpus.load("brutal");

        NodeMatcher.WorkJudge contrarian = new NodeMatcher.WorkJudge() {
            @Override
            public boolean isEnabled(Judgement.PlugPoint plugPoint) {
                return plugPoint == Judgement.PlugPoint.SAME_WORK;
            }

            @Override
            public Optional<Judgement.Verdict> judge(Judgement.Question question) {
                return Optional.of(new Judgement.Verdict("DIFFERENT", 0.99, "not the same work"));
            }
        };

        List<NodeVerdict> off = runOver(corpus, corpus.templates(), null);
        List<NodeVerdict> vetoed = runOver(corpus, corpus.templates(), contrarian);

        for (int at = 0; at < off.size(); at++) {
            if (off.get(at).tier() == MatchTier.ABSTAIN) {
                assertThat(vetoed.get(at).tier())
                        .as("a DIFFERENT verdict cannot promote %s", off.get(at).nodeId())
                        .isEqualTo(MatchTier.ABSTAIN);
            }
        }
    }
}
