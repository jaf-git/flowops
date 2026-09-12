package com.flowops.chat.application.buildprocess;

import java.util.Objects;

public record Sourced<T>(T value, FieldSource source) {
    public Sourced {
        Objects.requireNonNull(value, "a value is required");
        Objects.requireNonNull(source, "a value must say where it came from");
    }

    static <T> Sourced<T> quoted(T value) {
        return new Sourced<>(value, FieldSource.FROM_CONVERSATION);
    }

    static <T> Sourced<T> guessed(T value) {
        return new Sourced<>(value, FieldSource.SUGGESTED);
    }
}
