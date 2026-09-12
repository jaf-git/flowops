package com.flowops.nodepipeline.domain.match;

public record MatchWeights(
        double textWeight,
        double roleWeight,
        double shapeWeight,
        double outputWeight,
        double scoreFloor,
        double confidenceFloor,
        double separationMinimum,
        double textVeto,
        double textQualityFloor,
        int finalists,
        double plausibleFloor,
        double uniqueFloor,
        double keywordCorroboration,
        Gates gates) {
    public record Gates(
            boolean boundary,
            boolean closure,
            boolean intent,
            boolean direction,
            boolean postClose,
            boolean shapeFeature,
            boolean outputMap,
            boolean keywords,
            boolean typeContradiction,
            boolean markerAddress) {}

    public static MatchWeights reference() {
        return new MatchWeights(
                0.50,
                0.20,
                0.20,
                0.10,
                0.70,
                0.60,
                0.06,
                0.45,
                0.25,
                4,
                0.5,
                0.5,
                0.30,
                new Gates(true, true, true, true, false, true, true, true, true, true));
    }

    public static MatchWeights forOneSentence() {
        return new MatchWeights(
                0.50,
                0.0,
                0.0,
                0.0,
                0.85,
                0.60,
                0.06,
                0.45,
                0.25,
                4,
                0.5,
                0.5,
                0.30,
                new Gates(true, true, true, true, false, true, true, true, true, true));
    }

    public double totalWeight() {
        return textWeight + roleWeight + shapeWeight + outputWeight;
    }
}
