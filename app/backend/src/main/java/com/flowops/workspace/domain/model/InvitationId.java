package com.flowops.workspace.domain.model;

import java.util.Objects;
import java.util.UUID;

public record InvitationId(UUID value) {
    public InvitationId {
        Objects.requireNonNull(value, "an invitation identity is required");
    }

    public static InvitationId of(UUID value) {
        return new InvitationId(value);
    }
}
