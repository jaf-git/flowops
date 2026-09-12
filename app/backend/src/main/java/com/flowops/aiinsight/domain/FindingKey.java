package com.flowops.aiinsight.domain;

import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record FindingKey(String value) {
    private static final String SEPARATOR = String.valueOf((char) 0x1F);

    public FindingKey {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("a finding key identifies a finding and cannot be blank");
        }
    }

    public static FindingKey of(Object... parts) {
        return new FindingKey(Stream.of(parts).map(FindingKey::normalise).collect(Collectors.joining(SEPARATOR)));
    }

    private static String normalise(Object part) {
        if (part == null) {
            return "";
        }
        return part.toString()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
    }

    @Override
    public String toString() {
        return value;
    }
}
