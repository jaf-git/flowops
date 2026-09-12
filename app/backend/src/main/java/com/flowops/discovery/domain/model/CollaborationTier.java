package com.flowops.discovery.domain.model;

public enum CollaborationTier {
    DECLARED,

    STRONG,

    GOOD,

    WEAK;

    public boolean collapsesToOneStep() {
        return this == DECLARED || this == STRONG || this == GOOD;
    }
}
