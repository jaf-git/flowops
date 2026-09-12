package com.flowops.aiinsight.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum SubjectType {
    PROCESS_TEMPLATE("process_template"),

    TASK_TEMPLATE("task_template");

    private final String wireName;

    SubjectType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static Optional<SubjectType> ofWireName(String wireName) {
        if (wireName == null) {
            return Optional.empty();
        }
        String normalised = wireName.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(type -> type.wireName.equals(normalised))
                .findFirst();
    }
}
