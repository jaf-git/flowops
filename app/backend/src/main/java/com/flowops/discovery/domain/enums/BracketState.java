package com.flowops.discovery.domain.enums;

public enum BracketState {
    OPEN,
    WAITING,
    CLOSED,
    LAPSED;

    public boolean isLive() {
        return this == OPEN || this == WAITING;
    }

    public boolean isTerminal() {
        return !isLive();
    }
}
