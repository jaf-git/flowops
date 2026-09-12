package com.flowops.discovery.domain.enums;

public enum HandoverCause {
    ORDINARY,

    DEACTIVATION;

    public boolean disruptsTheTrack() {
        return this == DEACTIVATION;
    }
}
