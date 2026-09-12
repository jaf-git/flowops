package com.flowops.task.domain.model;

import java.util.Objects;
import java.util.UUID;

public record ChecklistItemId(UUID value) {
    public ChecklistItemId {
        Objects.requireNonNull(value);
    }

    public static ChecklistItemId generate() {
        return new ChecklistItemId(UUID.randomUUID());
    }

    public static ChecklistItemId of(UUID value) {
        return new ChecklistItemId(value);
    }
}
