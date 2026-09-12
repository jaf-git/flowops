package com.flowops.discovery.domain.enums;

public enum CloseKind {
    DELIVERED,

    DONE,

    DROPPED,

    LAPSED,

    PARENT_CLOSED,

    OVERRIDE,

    HANDED_OVER,

    CADENCE_CLOSED,

    MERGED,

    JOB_END;

    public boolean satisfiesAWait() {
        return this == DELIVERED || this == DONE;
    }

    public boolean countsAsPatternEvidence() {
        return this == DELIVERED || this == DONE || this == DROPPED;
    }

    public boolean leavesAHole() {
        return this == LAPSED || this == PARENT_CLOSED;
    }

    public boolean requiresOutput() {
        return this == DELIVERED;
    }

    public boolean requiresReason() {
        return this == DROPPED;
    }
}
