package com.flowops.task.domain.model;

import java.util.Objects;
import java.util.UUID;

public record TaskLinkId(UUID value) {
    public TaskLinkId {
        Objects.requireNonNull(value);
    }

    public static TaskLinkId generate() {
        return new TaskLinkId(UUID.randomUUID());
    }

    public static TaskLinkId of(UUID value) {
        return new TaskLinkId(value);
    }
}
