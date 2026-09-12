package com.flowops.analyser.domain;

public enum BracketOutcome {
    FINISHED,

    ABANDONED,

    CONTAINER_CLOSED,

    DISRUPTED,

    OPEN;

    public static BracketOutcome of(String closeKind) {
        if (closeKind == null) {
            return OPEN;
        }
        return switch (closeKind) {
            case "DELIVERED", "DONE" -> FINISHED;
            case "DROPPED" -> ABANDONED;
            case "JOB_END", "CADENCE_CLOSED", "PARENT_CLOSED" -> CONTAINER_CLOSED;
            case "HANDED_OVER" -> DISRUPTED;
            default -> OPEN;
        };
    }

    public boolean contributesADuration() {
        return this == FINISHED;
    }
}
