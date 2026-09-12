package com.flowops.process.domain.model;

import java.util.Objects;
import java.util.UUID;

public record TaskTemplateRef(UUID value) {
    public TaskTemplateRef {
        Objects.requireNonNull(value, "a task template identifier is required");
    }

    public static TaskTemplateRef of(UUID value) {
        return new TaskTemplateRef(value);
    }
}
