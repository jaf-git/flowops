package com.flowops.discovery.domain.model;

import java.util.UUID;

public record JobId(UUID value) {
    public JobId {
        if (value == null) {
            throw new IllegalArgumentException("a job identifier is required");
        }
    }

    public static JobId of(UUID value) {
        return new JobId(value);
    }
}
