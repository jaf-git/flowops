package com.flowops.tasklib.domain;

import java.util.Arrays;
import java.util.Optional;

public enum OutputKind {
    TEXT,

    DESIGN,

    REPORT,

    SCHEDULE,

    DECISION,

    PHYSICAL,

    NONE;

    public static Optional<OutputKind> named(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(kind -> kind.name().equalsIgnoreCase(name.trim()))
                .findFirst();
    }
}
