package com.flowops.task.domain.model;

import java.util.Objects;
import java.util.UUID;

public record TaskAmendmentId(UUID value) {
    public TaskAmendmentId {
        Objects.requireNonNull(value, "an identity is required");
    }

    public static TaskAmendmentId generate() {
        return new TaskAmendmentId(UUID.randomUUID());
    }

    public static TaskAmendmentId of(UUID value) {
        return new TaskAmendmentId(value);
    }
}
