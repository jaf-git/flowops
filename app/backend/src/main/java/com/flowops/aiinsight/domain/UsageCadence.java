package com.flowops.aiinsight.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class UsageCadence {
    public static final int MINIMUM_USES = 3;

    private static final double TOLERANCE_OF_PERIOD = 0.25;

    private static final double SHARE_THAT_MUST_AGREE = 0.6;

    private final Integer periodDays;

    private UsageCadence(Integer periodDays) {
        this.periodDays = periodDays;
    }

    public static UsageCadence none() {
        return new UsageCadence(null);
    }

    public static UsageCadence in(List<Instant> usedAt) {
        if (usedAt == null || usedAt.size() < MINIMUM_USES) {
            return none();
        }

        List<Instant> ordered = usedAt.stream().sorted().toList();
        List<Long> intervals = new ArrayList<>();
        for (int use = 1; use < ordered.size(); use++) {
            long days = Duration.between(ordered.get(use - 1), ordered.get(use)).toDays();

            if (days > 0) {
                intervals.add(days);
            }
        }
        if (intervals.size() < MINIMUM_USES - 1) {
            return none();
        }

        long period = median(intervals);
        double tolerance = period * TOLERANCE_OF_PERIOD;
        long agreeing = intervals.stream()
                .filter(gap -> Math.abs(gap - period) <= tolerance)
                .count();

        if (agreeing < Math.ceil(intervals.size() * SHARE_THAT_MUST_AGREE)) {
            return none();
        }
        return new UsageCadence((int) period);
    }

    public boolean isDetected() {
        return periodDays != null;
    }

    public Integer periodDays() {
        return periodDays;
    }

    public int idleAfterDays(int fallbackWindowDays) {
        if (periodDays == null) {
            return fallbackWindowDays;
        }
        return (int) Math.round(periodDays * (1 + TOLERANCE_OF_PERIOD));
    }

    private static long median(List<Long> intervals) {
        List<Long> sorted = intervals.stream().sorted().toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2;
    }
}
