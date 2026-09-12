package com.flowops.shared.text;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public final class WorkTypeWords {
    private static final String ARROW = "→";

    private WorkTypeWords() {}

    public static Optional<String> of(String workType) {
        if (workType == null || workType.isBlank()) {
            return Optional.empty();
        }
        String words = workType.trim().replace('_', ' ').toLowerCase(Locale.ROOT);
        return Optional.of(words.substring(0, 1).toUpperCase(Locale.ROOT) + words.substring(1));
    }

    public static String shape(String subjectKey) {
        if (subjectKey == null || subjectKey.isBlank()) {
            return "";
        }
        return Arrays.stream(subjectKey.split(ARROW))
                .map(String::trim)
                .filter(step -> !step.isEmpty())
                .map(step -> of(step).orElse(step))
                .collect(Collectors.joining(" " + ARROW + " "));
    }
}
