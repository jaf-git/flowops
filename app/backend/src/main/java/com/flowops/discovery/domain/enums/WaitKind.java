package com.flowops.discovery.domain.enums;

import java.time.Duration;

public enum WaitKind {
    CLIENT(Duration.ofDays(5)),
    SUPPLIER(Duration.ofDays(5)),
    COLLEAGUE(Duration.ofDays(2)),
    APPROVAL(Duration.ofDays(2));

    private final Duration defaultExpectation;

    WaitKind(Duration defaultExpectation) {
        this.defaultExpectation = defaultExpectation;
    }

    public boolean isExternal() {
        return this == CLIENT || this == SUPPLIER;
    }

    public Duration defaultExpectation() {
        return defaultExpectation;
    }
}
