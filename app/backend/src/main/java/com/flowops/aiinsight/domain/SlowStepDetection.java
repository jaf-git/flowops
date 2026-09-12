package com.flowops.aiinsight.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SlowStepDetection {
    public static final int RUNS_REQUIRED = 3;

    private SlowStepDetection() {}

    public record StepDuration(String plannedTitle, long workMs, long blockedMs, long waitingMs, long reviewMs) {
        long elapsedMs() {
            return workMs + blockedMs + waitingMs + reviewMs;
        }
    }

    public record SlowStep(
            String title,
            long medianElapsedMs,
            long medianWorkMs,
            long medianBlockedMs,
            long medianWaitingMs,
            long medianReviewMs,
            int stepsCounted) {}

    public static Optional<SlowStep> over(List<StepDuration> steps, int completedRuns) {
        if (completedRuns < RUNS_REQUIRED || steps.isEmpty()) {
            return Optional.empty();
        }

        Map<String, List<StepDuration>> byTitle = new LinkedHashMap<>();
        for (StepDuration step : steps) {
            byTitle.computeIfAbsent(step.plannedTitle(), any -> new ArrayList<>())
                    .add(step);
        }
        if (byTitle.size() < 2) {
            return Optional.empty();
        }

        List<SlowStep> ranked = new ArrayList<>();
        byTitle.forEach((title, occurrences) -> ranked.add(new SlowStep(
                title,
                medianOf(occurrences, StepDuration::elapsedMs),
                medianOf(occurrences, StepDuration::workMs),
                medianOf(occurrences, StepDuration::blockedMs),
                medianOf(occurrences, StepDuration::waitingMs),
                medianOf(occurrences, StepDuration::reviewMs),
                occurrences.size())));

        ranked.sort(Comparator.comparingLong(SlowStep::medianElapsedMs).reversed());
        SlowStep slowest = ranked.get(0);

        return slowest.medianElapsedMs() == 0 ? Optional.empty() : Optional.of(slowest);
    }

    private static long medianOf(List<StepDuration> steps, java.util.function.ToLongFunction<StepDuration> of) {
        List<Long> values = steps.stream().map(of::applyAsLong).sorted().toList();
        int middle = values.size() / 2;
        return values.size() % 2 == 1 ? values.get(middle) : (values.get(middle - 1) + values.get(middle)) / 2;
    }
}
