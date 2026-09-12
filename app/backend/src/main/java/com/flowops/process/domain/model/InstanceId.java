package com.flowops.process.domain.model;

import java.util.Objects;
import java.util.UUID;

public record InstanceId(UUID value) {
    public InstanceId {
        Objects.requireNonNull(value, "an instance identifier is required");
    }

    public static InstanceId of(UUID value) {
        return new InstanceId(value);
    }
}
