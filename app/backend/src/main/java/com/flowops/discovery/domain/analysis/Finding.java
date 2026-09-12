package com.flowops.discovery.domain.analysis;

import com.flowops.discovery.domain.model.BracketId;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record Finding(
        String detector,
        SubjectKind subjectKind,
        String subjectKey,
        String headline,
        int sampleSize,
        BigDecimal measure,
        String unit,
        List<BracketId> subjects) {
    public Finding {
        detector = requireText(detector, "a finding names the detector that produced it");
        Objects.requireNonNull(subjectKind, "a finding is about something");
        subjectKey = requireText(subjectKey, "a finding names its subject");
        headline = requireText(headline, "a finding says something");

        if (sampleSize < 0) {
            throw new IllegalArgumentException("a sample size is a count of cases, never negative");
        }

        subjects = List.copyOf(subjects);
    }

    public static Finding measured(
            String detector,
            SubjectKind kind,
            String key,
            String headline,
            int sampleSize,
            BigDecimal measure,
            String unit,
            List<BracketId> subjects) {
        return new Finding(detector, kind, key, headline, sampleSize, measure, unit, subjects);
    }

    public static Finding counted(
            String detector, SubjectKind kind, String key, String headline, int count, List<BracketId> subjects) {
        return new Finding(detector, kind, key, headline, count, BigDecimal.valueOf(count), "cases", subjects);
    }

    public Optional<BigDecimal> figure() {
        return Optional.ofNullable(measure);
    }

    public boolean restsOnEnough(int floor) {
        return sampleSize >= floor;
    }

    private static String requireText(String value, String why) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(why);
        }
        return value.trim();
    }
}
