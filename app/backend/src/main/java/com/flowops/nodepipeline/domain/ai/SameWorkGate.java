package com.flowops.nodepipeline.domain.ai;

import com.flowops.nodepipeline.domain.MatchTier;

public final class SameWorkGate {
    private SameWorkGate() {}

    public static boolean inUncertaintyBand(double textScore, double score, double textVeto, double scoreFloor) {
        return textScore >= textVeto && score < scoreFloor;
    }

    public static MatchTier decide(
            MatchTier deterministic, Judgement.Verdict verdict, boolean failedOnScoreAlone, double confidenceFloor) {
        if (verdict == null) {
            return deterministic;
        }
        java.util.Optional<Judgement.SameWork> opinion = verdict.asSameWork();
        if (opinion.isEmpty()) {
            return deterministic;
        }

        return switch (opinion.get()) {
            case DIFFERENT -> MatchTier.ABSTAIN;

            case UNSURE -> deterministic == MatchTier.NUDGE ? MatchTier.ABSTAIN : deterministic;

            case SAME -> deterministic == MatchTier.ABSTAIN
                            && failedOnScoreAlone
                            && verdict.confidence() >= confidenceFloor
                    ? MatchTier.NUDGE
                    : deterministic;
        };
    }
}
