package com.flowops.discovery.domain.enums;

public enum Direction {
    REQUEST,

    COMPLETION,

    STANDALONE,

    QUERY;

    public boolean canJoinATrack() {
        return this != QUERY;
    }
}
