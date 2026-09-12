package com.flowops.workspace.domain.model;

import java.util.Objects;
import java.util.UUID;

public record PersonId(UUID value) {
    public PersonId {
        Objects.requireNonNull(value, "a person identity is required");
    }

    public static PersonId of(UUID value) {
        return new PersonId(value);
    }
}
