package com.flowops.discovery.domain.enums;

public enum TrackTypeStatus {
    CANDIDATE,

    PROPOSED,

    NAMED,

    PROVISIONAL,

    REJECTED,

    SUPERSEDED;

    public boolean isTerminal() {
        return this == REJECTED || this == SUPERSEDED;
    }

    public boolean wasNamed() {
        return this == NAMED || this == PROVISIONAL;
    }
}
