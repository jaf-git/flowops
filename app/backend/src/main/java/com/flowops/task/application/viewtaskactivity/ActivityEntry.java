package com.flowops.task.application.viewtaskactivity;

import com.flowops.task.domain.enums.TaskState;
import com.flowops.task.domain.model.PersonId;
import java.time.Instant;

public record ActivityEntry(
        String kind,
        Instant occurredAt,
        PersonId actor,
        String actorName,
        TaskState from,
        TaskState to,
        String reason,
        boolean overridden,
        String body) {
    public static final String TRANSITION = "TRANSITION";
    public static final String COMMENT = "COMMENT";
}
