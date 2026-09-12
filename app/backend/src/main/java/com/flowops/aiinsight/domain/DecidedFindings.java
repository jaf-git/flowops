package com.flowops.aiinsight.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DecidedFindings {
    private final Map<InsightIdentity, RecordedDecision> newestByFinding;

    private DecidedFindings(Map<InsightIdentity, RecordedDecision> newestByFinding) {
        this.newestByFinding = newestByFinding;
    }

    public static DecidedFindings from(List<RecordedDecision> decisions) {
        Map<InsightIdentity, RecordedDecision> newest = new LinkedHashMap<>();
        for (RecordedDecision decision : decisions) {
            newest.putIfAbsent(decision.identity(), decision);
        }
        return new DecidedFindings(newest);
    }

    public static DecidedFindings none() {
        return new DecidedFindings(Map.of());
    }

    public boolean suppresses(InsightIdentity identity, SubjectFingerprint shapeNow) {
        RecordedDecision decided = newestByFinding.get(identity);
        if (decided == null) {
            return false;
        }
        return switch (decided.outcome()) {
            case APPLIED -> true;
            case DISMISSED -> shapeNow.stillMatches(decided.fingerprintAtDecision());
        };
    }
}
