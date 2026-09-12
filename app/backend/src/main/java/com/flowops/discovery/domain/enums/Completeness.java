package com.flowops.discovery.domain.enums;

public enum Completeness {
    COMPLETE,

    PARTIAL,

    START_ONLY;

    public boolean qualifiesForDiscovery() {
        return this != START_ONLY;
    }
}
