package com.flowops.chat.domain.model;

import java.util.Objects;
import java.util.UUID;

public record PersonId(UUID value) implements Comparable<PersonId> {
    public PersonId {
        Objects.requireNonNull(value, "a person identifier is required");
    }

    public static PersonId of(UUID value) {
        return new PersonId(value);
    }

    @Override
    public int compareTo(PersonId other) {
        int mostSignificant =
                Long.compareUnsigned(value.getMostSignificantBits(), other.value.getMostSignificantBits());
        return mostSignificant != 0
                ? mostSignificant
                : Long.compareUnsigned(value.getLeastSignificantBits(), other.value.getLeastSignificantBits());
    }
}
