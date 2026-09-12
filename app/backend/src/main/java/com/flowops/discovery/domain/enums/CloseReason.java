package com.flowops.discovery.domain.enums;

public enum CloseReason {
    TERMINAL_OUTPUT,

    DORMANT,

    JOB_CLOSED,

    PERFORMER_GONE,

    REASSIGNED;

    public boolean qualifiesForDiscovery() {
        return this == TERMINAL_OUTPUT;
    }
}
