package com.flowops.tasklib.domain;

public record TemplatePerformance(
        int stamped,
        int measuredTasks,
        Long medianActiveSeconds,
        Long lowerQuartileSeconds,
        Long upperQuartileSeconds,
        int passedFirstTime,
        int reviewed) {
    public static final int ENOUGH_TO_AVERAGE = 3;

    public boolean tooFewToAverage() {
        return measuredTasks < ENOUGH_TO_AVERAGE;
    }

    public boolean neverUsed() {
        return stamped == 0;
    }

    public boolean varyWidely() {
        if (tooFewToAverage() || lowerQuartileSeconds == null || upperQuartileSeconds == null) {
            return false;
        }
        return lowerQuartileSeconds > 0 && upperQuartileSeconds >= lowerQuartileSeconds * 2;
    }
}
