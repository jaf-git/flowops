package com.flowops.workspace.domain.model;

import java.util.Objects;
import java.util.UUID;

public record ConsentRecordId(UUID value) {
    public ConsentRecordId {
        Objects.requireNonNull(value, "a consent record identity is required");
    }

    public static ConsentRecordId of(UUID value) {
        return new ConsentRecordId(value);
    }

    public static ConsentRecordId generate() {
        return new ConsentRecordId(UUID.randomUUID());
    }
}
