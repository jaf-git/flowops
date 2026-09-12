package com.flowops.workspace.domain.model;

import java.util.Objects;

public record InvitationToken(String value) {
    public InvitationToken {
        Objects.requireNonNull(value, "a token is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException("a token is required");
        }
    }

    public static InvitationToken of(String value) {
        return new InvitationToken(value);
    }

    @Override
    public String toString() {
        return "InvitationToken[redacted]";
    }
}
