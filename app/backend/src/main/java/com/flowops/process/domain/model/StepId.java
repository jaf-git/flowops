package com.flowops.process.domain.model;

import java.util.Objects;
import java.util.UUID;

public record StepId(UUID value) {
    public StepId {
        Objects.requireNonNull(value, "a step identifier is required");
    }

    public static StepId of(UUID value) {
        return new StepId(value);
    }
}
