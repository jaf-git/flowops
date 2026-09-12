package com.flowops.discovery.domain.model;

import java.util.UUID;

public record WorkNodeId(UUID value) {
    public WorkNodeId {
        if (value == null) {
            throw new IllegalArgumentException("a work node identifier is required");
        }
    }

    public static WorkNodeId of(UUID value) {
        return new WorkNodeId(value);
    }
}
