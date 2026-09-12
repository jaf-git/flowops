package com.flowops.task.domain.model;

import java.util.Objects;
import java.util.UUID;

public record CompletionProofId(UUID value) {
    public CompletionProofId {
        Objects.requireNonNull(value, "a completion proof identity is required");
    }

    public static CompletionProofId generate() {
        return new CompletionProofId(UUID.randomUUID());
    }

    public static CompletionProofId of(UUID value) {
        return new CompletionProofId(value);
    }
}
