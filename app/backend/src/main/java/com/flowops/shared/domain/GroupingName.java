package com.flowops.shared.domain;

import java.util.Locale;

public record GroupingName(String value) {
    public static final int LONGEST = 80;

    public static GroupingName of(String typed) {
        return new GroupingName(typed == null ? "" : typed.strip());
    }

    public String normalised() {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    public boolean sameAs(GroupingName other) {
        return other != null && normalised().equals(other.normalised());
    }

    public boolean isBlank() {
        return value.isBlank();
    }

    @Override
    public String toString() {
        return value;
    }
}
