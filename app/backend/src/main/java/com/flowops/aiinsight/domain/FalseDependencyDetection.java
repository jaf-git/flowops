package com.flowops.aiinsight.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class FalseDependencyDetection {
    public static final int RUNS_REQUIRED = 3;

    private static final String SEPARATOR = "\u0000";

    private FalseDependencyDetection() {}

    public record EdgeObservation(
            UUID instanceId, String dependentTitle, String dependsOnTitle, boolean dependentStartedEarly) {}

    public record FalseDependency(
            String dependentTitle, String dependsOnTitle, int runsObserved, List<UUID> instances) {}

    public static List<FalseDependency> over(List<EdgeObservation> observations) {
        Map<String, List<EdgeObservation>> byEdge = new LinkedHashMap<>();
        for (EdgeObservation observation : observations) {
            if (observation.dependentTitle() == null || observation.dependsOnTitle() == null) {
                continue;
            }

            byEdge.computeIfAbsent(key(observation), each -> new ArrayList<>()).add(observation);
        }

        List<FalseDependency> found = new ArrayList<>();
        for (List<EdgeObservation> forOneEdge : byEdge.values()) {
            Set<UUID> runs = new LinkedHashSet<>();
            boolean waitedAtLeastOnce = false;
            for (EdgeObservation observation : forOneEdge) {
                runs.add(observation.instanceId());
                if (!observation.dependentStartedEarly()) {
                    waitedAtLeastOnce = true;
                }
            }

            if (waitedAtLeastOnce || runs.size() < RUNS_REQUIRED) {
                continue;
            }
            EdgeObservation first = forOneEdge.get(0);
            found.add(new FalseDependency(
                    first.dependentTitle(), first.dependsOnTitle(), runs.size(), List.copyOf(runs)));
        }

        found.sort(Comparator.comparingInt(FalseDependency::runsObserved).reversed());
        return found;
    }

    private static String key(EdgeObservation observation) {
        return observation.dependentTitle() + SEPARATOR + observation.dependsOnTitle();
    }
}
