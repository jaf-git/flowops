package com.flowops.aiinsight.domain;

public sealed interface InsightAction {
    record InsertStep(String title, int position) implements InsightAction {}

    record RemoveDependency(String dependentTitle, String dependsOnTitle) implements InsightAction {}

    record RetireTemplate() implements InsightAction {}

    record UpdateEstimate(long medianWorkMs) implements InsightAction {}
}
