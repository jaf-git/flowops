package com.flowops.discovery.domain.enums;

public enum CounterpartyKind {
    CLIENT,

    INTERNAL,

    SUPPLIER,

    PROSPECT,

    UNCLASSIFIED;

    public boolean isClassified() {
        return this != UNCLASSIFIED;
    }
}
