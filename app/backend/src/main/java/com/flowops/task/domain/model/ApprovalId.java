package com.flowops.task.domain.model;

import java.util.Objects;
import java.util.UUID;

public record ApprovalId(UUID value) {
    public ApprovalId {
        Objects.requireNonNull(value, "an approval identity is required");
    }

    public static ApprovalId generate() {
        return new ApprovalId(UUID.randomUUID());
    }

    public static ApprovalId of(UUID value) {
        return new ApprovalId(value);
    }
}
