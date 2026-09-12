package com.flowops.discovery.domain.enums;

public enum TrackState {
    OPEN,

    ACTIVE,

    DORMANT,

    DISRUPTED,

    CLOSED;

    public boolean isClosed() {
        return this == CLOSED;
    }
}
