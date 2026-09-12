package com.flowops.discovery.domain.model;

import java.util.UUID;

public record BracketId(UUID value) {
    public BracketId {
        if (value == null) {
            throw new IllegalArgumentException("a bracket identifier is required");
        }
    }

    public static BracketId of(UUID value) {
        return new BracketId(value);
    }

    public static BracketId fresh() {
        return new BracketId(UUID.randomUUID());
    }
}
