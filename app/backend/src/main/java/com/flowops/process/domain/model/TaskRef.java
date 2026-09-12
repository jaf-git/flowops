package com.flowops.process.domain.model;

import java.util.Objects;
import java.util.UUID;

public record TaskRef(UUID value) {
    public TaskRef {
        Objects.requireNonNull(value, "a task reference is required");
    }

    public static TaskRef of(UUID value) {
        return new TaskRef(value);
    }
}
