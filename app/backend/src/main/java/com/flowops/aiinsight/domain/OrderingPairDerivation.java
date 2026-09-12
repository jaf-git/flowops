package com.flowops.aiinsight.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class OrderingPairDerivation {
    private OrderingPairDerivation() {}

    public record TaskObservation(UUID taskId, UUID runId, String normalisedTitle, Instant closedAt) {
        public boolean belongsToARun() {
            return runId != null;
        }

        boolean canBeOrdered() {
            return belongsToARun() && closedAt != null && normalisedTitle != null;
        }
    }

    public record OrderingPair(String earlierTitle, String laterTitle, int runsObserved) {}

    public static List<OrderingPair> over(List<TaskObservation> observations) {
        Map<UUID, List<TaskObservation>> byRun = new LinkedHashMap<>();
        for (TaskObservation task : observations) {
            if (task.canBeOrdered()) {
                byRun.computeIfAbsent(task.runId(), run -> new ArrayList<>()).add(task);
            }
        }

        Map<Ordering, Integer> runsPerOrdering = new LinkedHashMap<>();
        for (List<TaskObservation> run : byRun.values()) {
            for (Ordering ordering : adjacentOrderingsWithin(run)) {
                runsPerOrdering.merge(ordering, 1, Integer::sum);
            }
        }

        List<OrderingPair> pairs = new ArrayList<>();
        runsPerOrdering.forEach(
                (ordering, runs) -> pairs.add(new OrderingPair(ordering.earlier(), ordering.later(), runs)));
        pairs.sort((first, second) -> Integer.compare(second.runsObserved(), first.runsObserved()));
        return List.copyOf(pairs);
    }

    public static Map<String, Integer> recurrenceOf(List<TaskObservation> observations) {
        Map<String, Integer> counts = new HashMap<>();
        for (TaskObservation task : observations) {
            if (task.normalisedTitle() != null) {
                counts.merge(task.normalisedTitle(), 1, Integer::sum);
            }
        }
        return Map.copyOf(counts);
    }

    public static boolean shareARun(List<TaskObservation> observations) {
        Set<UUID> seen = new HashSet<>();
        for (TaskObservation task : observations) {
            if (task.belongsToARun() && !seen.add(task.runId())) {
                return true;
            }
        }
        return false;
    }

    private record Ordering(String earlier, String later) {}

    private static List<Ordering> adjacentOrderingsWithin(List<TaskObservation> run) {
        List<TaskObservation> ordered = run.stream()
                .sorted((first, second) -> first.closedAt().compareTo(second.closedAt()))
                .toList();

        List<Ordering> orderings = new ArrayList<>();
        for (int step = 1; step < ordered.size(); step++) {
            String earlier = ordered.get(step - 1).normalisedTitle();
            String later = ordered.get(step).normalisedTitle();

            if (!earlier.equals(later)) {
                orderings.add(new Ordering(earlier, later));
            }
        }

        return orderings.stream().distinct().toList();
    }
}
