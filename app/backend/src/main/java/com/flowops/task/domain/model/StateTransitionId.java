package com.flowops.task.domain.model;

import java.util.Objects;
import java.util.UUID;

public record StateTransitionId(UUID value) {
    public StateTransitionId {
        Objects.requireNonNull(value, "a transition identity is required");
    }

    public static StateTransitionId generate() {
        return new StateTransitionId(UUID.randomUUID());
    }

    public static StateTransitionId of(UUID value) {
        return new StateTransitionId(value);
    }
}
