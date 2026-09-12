package com.flowops.aiinsight.domain;

import java.util.List;
import java.util.Optional;

public final class EstimateDivergenceDetection {
    public static final int MINIMUM_CLOSED_TASKS = 5;

    private static final double TOLERANCE_OF_ESTIMATE = 0.25;

    private static final double SPREAD_WORTH_STATING = 2.0;

    private EstimateDivergenceDetection() {}

    public record ClosedTask(long workMs, Long estimatedMsWhenStamped) {}

    public record Divergence(
            Long estimatedMs, long medianWorkMs, long fastestMiddleMs, long slowestMiddleMs, boolean spreadIsWide) {
        public boolean correctsAnEstimate() {
            return estimatedMs != null;
        }
    }

    public static Optional<Divergence> in(List<ClosedTask> closed, Long currentEstimateMs) {
        if (closed == null || closed.size() < MINIMUM_CLOSED_TASKS) {
            return Optional.empty();
        }

        List<Long> workTimes = closed.stream()
                .map(ClosedTask::workMs)
                .filter(ms -> ms > 0)
                .sorted()
                .toList();
        if (workTimes.size() < MINIMUM_CLOSED_TASKS) {
            return Optional.empty();
        }

        long median = median(workTimes);

        Long estimate = snapshotMedian(closed).orElse(currentEstimateMs);

        if (estimate != null && withinTolerance(median, estimate)) {
            return Optional.empty();
        }

        long fastestMiddle = quartile(workTimes, 0.25);
        long slowestMiddle = quartile(workTimes, 0.75);
        boolean wide = fastestMiddle > 0 && slowestMiddle >= fastestMiddle * SPREAD_WORTH_STATING;

        return Optional.of(new Divergence(estimate, median, fastestMiddle, slowestMiddle, wide));
    }

    private static boolean withinTolerance(long median, long estimate) {
        return Math.abs(median - estimate) <= estimate * TOLERANCE_OF_ESTIMATE;
    }

    private static Optional<Long> snapshotMedian(List<ClosedTask> closed) {
        List<Long> stamped = closed.stream()
                .map(ClosedTask::estimatedMsWhenStamped)
                .filter(java.util.Objects::nonNull)
                .sorted()
                .toList();
        if (stamped.size() * 2 <= closed.size()) {
            return Optional.empty();
        }
        return Optional.of(median(stamped));
    }

    private static long median(List<Long> sorted) {
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2;
    }

    private static long quartile(List<Long> sorted, double at) {
        int index = (int) Math.min(sorted.size() - 1L, Math.max(0L, Math.round(at * (sorted.size() - 1))));
        return sorted.get(index);
    }
}
