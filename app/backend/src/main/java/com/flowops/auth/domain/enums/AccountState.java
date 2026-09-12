package com.flowops.auth.domain.enums;

public enum AccountState {
    INVITED,
    ACTIVE,
    DEACTIVATED,

    ERASED;

    public boolean permitsAuthentication() {
        return this == ACTIVE;
    }
}
