package com.flowops.task.domain.model;

import java.util.Objects;
import java.util.UUID;

public record TaskId(UUID value) {
    public TaskId {
        Objects.requireNonNull(value, "a task identity is required");
    }

    public static TaskId generate() {
        return new TaskId(UUID.randomUUID());
    }

    public static TaskId of(UUID value) {
        return new TaskId(value);
    }
}
