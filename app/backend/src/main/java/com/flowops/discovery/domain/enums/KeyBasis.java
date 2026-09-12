package com.flowops.discovery.domain.enums;

public enum KeyBasis {
    ROLE_PAIR,

    PERFORMER;

    public boolean isWeak() {
        return this == PERFORMER;
    }
}
