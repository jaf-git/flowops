package com.flowops.task.domain.model;

import java.util.Objects;
import java.util.UUID;

public record PhaseTimerId(UUID value) {
    public PhaseTimerId {
        Objects.requireNonNull(value, "a phase timer identity is required");
    }

    public static PhaseTimerId generate() {
        return new PhaseTimerId(UUID.randomUUID());
    }

    public static PhaseTimerId of(UUID value) {
        return new PhaseTimerId(value);
    }
}
