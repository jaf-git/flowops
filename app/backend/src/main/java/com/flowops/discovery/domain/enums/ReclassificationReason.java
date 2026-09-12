package com.flowops.discovery.domain.enums;

public enum ReclassificationReason {
    CONVERSION,

    CORRECTION;

    public boolean isRetroactive() {
        return this == CORRECTION;
    }
}
