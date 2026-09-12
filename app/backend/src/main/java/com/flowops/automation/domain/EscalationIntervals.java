package com.flowops.automation.domain;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public record EscalationIntervals(List<Duration> ascending) {
    public EscalationIntervals {
        ascending = List.copyOf(ascending);
    }

    public static EscalationIntervals fromHours(String commaSeparatedHours) {
        if (commaSeparatedHours == null || commaSeparatedHours.isBlank()) {
            return new EscalationIntervals(List.of());
        }
        List<Duration> read = new java.util.ArrayList<>();
        for (String piece : commaSeparatedHours.split(",")) {
            long hours;
            try {
                hours = Long.parseLong(piece.trim());
            } catch (NumberFormatException unreadable) {
                break;
            }
            Duration interval = Duration.ofHours(hours);
            if (interval.isZero() || interval.isNegative()) {
                break;
            }
            if (!read.isEmpty() && interval.compareTo(read.get(read.size() - 1)) <= 0) {
                break;
            }
            read.add(interval);
        }
        return new EscalationIntervals(read);
    }

    public Optional<Duration> before(Rung rung) {
        int position = rung.index() - 1;
        return position >= 0 && position < ascending.size() ? Optional.of(ascending.get(position)) : Optional.empty();
    }
}
