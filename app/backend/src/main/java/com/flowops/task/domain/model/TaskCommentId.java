package com.flowops.task.domain.model;

import java.util.Objects;
import java.util.UUID;

public record TaskCommentId(UUID value) {
    public TaskCommentId {
        Objects.requireNonNull(value);
    }

    public static TaskCommentId generate() {
        return new TaskCommentId(UUID.randomUUID());
    }

    public static TaskCommentId of(UUID value) {
        return new TaskCommentId(value);
    }
}
