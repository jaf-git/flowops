package com.flowops.task.domain.model;

import java.util.Objects;
import java.util.UUID;

public record DeadlineProposalId(UUID value) {
    public DeadlineProposalId {
        Objects.requireNonNull(value, "an identity is required");
    }

    public static DeadlineProposalId generate() {
        return new DeadlineProposalId(UUID.randomUUID());
    }

    public static DeadlineProposalId of(UUID value) {
        return new DeadlineProposalId(value);
    }
}
