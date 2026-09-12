package com.flowops.workspace.domain.model;

import java.util.Objects;
import java.util.UUID;

public record MembershipId(UUID value) {
    public MembershipId {
        Objects.requireNonNull(value, "a membership identity is required");
    }

    public static MembershipId of(UUID value) {
        return new MembershipId(value);
    }
}
