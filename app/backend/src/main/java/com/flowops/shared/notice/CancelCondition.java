package com.flowops.shared.notice;

public enum CancelCondition {
    NEVER,

    TASK_LEFT_CREATED,

    TASK_LEFT_IN_PROGRESS,

    TASK_REASSIGNED,

    PROPOSAL_DECIDED,

    TASK_LEFT_OVERDUE,

    TASK_UNBLOCKED,

    TASK_LEFT_REVIEW,

    STEP_ASSIGNED,

    TASK_DETACHED,

    EVERY_SECTION_EMPTY,

    BRACKET_CLOSED
}
