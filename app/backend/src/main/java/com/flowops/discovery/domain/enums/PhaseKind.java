package com.flowops.discovery.domain.enums;

public enum PhaseKind {
    WORK,

    EXTERNAL_WAIT,

    INTERNAL_WAIT,

    REVIEW;

    public boolean isExternalWait() {
        return this == EXTERNAL_WAIT;
    }

    public boolean isWait() {
        return this == EXTERNAL_WAIT || this == INTERNAL_WAIT;
    }
}
