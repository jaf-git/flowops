package com.flowops.process.domain.model;

import java.util.Objects;
import java.util.UUID;

public record PersonId(UUID value) {
    public PersonId {
        Objects.requireNonNull(value, "a person identifier is required");
    }

    public static PersonId of(UUID value) {
        return new PersonId(value);
    }
}
