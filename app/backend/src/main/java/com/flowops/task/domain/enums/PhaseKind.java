package com.flowops.task.domain.enums;

public enum PhaseKind {
    WAIT,

    ACTIVE,

    BLOCKED,

    REVIEW,

    APPROVAL;

    public boolean countsAgainstTheAssignee() {
        return this == ACTIVE;
    }
}
