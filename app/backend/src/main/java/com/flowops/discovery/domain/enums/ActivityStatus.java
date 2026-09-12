package com.flowops.discovery.domain.enums;

public enum ActivityStatus {
    ACTIVE,
    MERGED,
    RETIRED;

    public boolean isChoosable() {
        return this == ACTIVE;
    }
}
