package com.flowops.aiinsight.domain;

public record RecordedDecision(InsightIdentity identity, DecisionOutcome outcome, String fingerprintAtDecision) {}
