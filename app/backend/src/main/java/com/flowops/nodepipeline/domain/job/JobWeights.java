package com.flowops.nodepipeline.domain.job;

public record JobWeights(
        double coverWeight,
        double explainedWeight,
        double extraWeight,
        double coverMinimum,
        double extraMaximum,
        double separationMinimum,
        int minimumJobNodes,
        int staleDays,
        boolean prefixStall,
        boolean fallthrough,
        boolean explanatoryTiebreak) {
    public static JobWeights reference() {
        return new JobWeights(0.40, 0.40, 0.20, 0.55, 0.60, 0.10, 3, 21, true, true, true);
    }
}
