package com.flowops.discovery.domain.enums;

public enum WorkNodeState {
    MARKED,

    ASSIGNED,

    SELF,

    BOUNCED,

    IN_PROGRESS,

    BLOCKED,

    COMPLETED,

    CLOSED,

    LAPSED,

    QUERY,

    ANSWERED;

    public boolean isTerminal() {
        return this == CLOSED || this == LAPSED || this == ANSWERED;
    }
}
