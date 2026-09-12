package com.flowops.discovery.domain.enums;

public enum OutputType {
    TEXT,
    DESIGN,
    REPORT,
    SCHEDULING,

    NONE;

    public boolean closesTheNode() {
        return this != NONE;
    }
}
