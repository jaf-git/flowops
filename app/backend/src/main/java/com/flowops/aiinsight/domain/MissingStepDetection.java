package com.flowops.aiinsight.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MissingStepDetection {
    public static final int OCCURRENCES_REQUIRED = 3;

    private MissingStepDetection() {}

    public record AttachedStep(UUID instanceId, String title, int position) {}

    public record MissingStep(String title, int position, int runsThatAddedIt, Set<UUID> instances) {}

    public static List<MissingStep> over(List<AttachedStep> attached, int completedRuns) {
        if (completedRuns < OCCURRENCES_REQUIRED) {
            return List.of();
        }

        Map<String, Set<UUID>> instancesByGroup = new LinkedHashMap<>();
        Map<String, String> titleOfGroup = new LinkedHashMap<>();
        Map<String, Integer> positionOfGroup = new LinkedHashMap<>();

        for (AttachedStep step : attached) {
            String key = normalise(step.title()) + "@" + step.position();
            instancesByGroup.computeIfAbsent(key, any -> new LinkedHashSet<>()).add(step.instanceId());

            titleOfGroup.putIfAbsent(key, step.title());
            positionOfGroup.putIfAbsent(key, step.position());
        }

        List<MissingStep> found = new ArrayList<>();
        instancesByGroup.forEach((key, instances) -> {
            if (instances.size() >= OCCURRENCES_REQUIRED) {
                found.add(
                        new MissingStep(titleOfGroup.get(key), positionOfGroup.get(key), instances.size(), instances));
            }
        });

        found.sort(Comparator.comparingInt(MissingStep::runsThatAddedIt).reversed());
        return List.copyOf(found);
    }

    static String normalise(String title) {
        return title.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
    }
}
