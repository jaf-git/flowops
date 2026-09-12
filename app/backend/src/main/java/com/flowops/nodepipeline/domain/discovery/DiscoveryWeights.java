package com.flowops.nodepipeline.domain.discovery;

public record DiscoveryWeights(
        int minimumNodesPerKind,
        int minimumRunsPerProcess,
        int stepTolerance,
        int orderMinimumRuns,
        double orderConfidenceFloor,
        double textQualityFloor,
        double plausibleFloor,
        double uniqueFloor,
        boolean conversationSplit) {
    public DiscoveryWeights {
        if (stepTolerance < 0) {
            throw new IllegalArgumentException(
                    "a negative step tolerance admits nothing, not even an identical engagement: " + stepTolerance);
        }
    }

    public static DiscoveryWeights reference() {
        return new DiscoveryWeights(3, 3, 0, 6, 0.85, 0.25, 0.5, 0.5, true);
    }
}
