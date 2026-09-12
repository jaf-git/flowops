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

public final class BlockPatternDetection {
    public static final int OCCURRENCES_REQUIRED = 3;

    private BlockPatternDetection() {}

    public record BlockOccurrence(UUID instanceId, String plannedTitle, String reason) {}

    public record BlockPattern(String stepTitle, String reason, int occurrences, Set<UUID> instances) {}

    private record Key(String stepTitle, String normalisedReason) {}

    public static List<BlockPattern> over(List<BlockOccurrence> blocks) {
        Map<Key, List<BlockOccurrence>> grouped = new LinkedHashMap<>();
        for (BlockOccurrence block : blocks) {
            if (block.reason() == null || block.reason().isBlank()) {
                continue;
            }
            grouped.computeIfAbsent(new Key(block.plannedTitle(), normalise(block.reason())), any -> new ArrayList<>())
                    .add(block);
        }

        List<BlockPattern> found = new ArrayList<>();
        grouped.forEach((key, occurrences) -> {
            if (occurrences.size() >= OCCURRENCES_REQUIRED) {
                Set<UUID> runs = new LinkedHashSet<>();
                occurrences.forEach(occurrence -> runs.add(occurrence.instanceId()));
                found.add(new BlockPattern(
                        occurrences.get(0).plannedTitle(), occurrences.get(0).reason(), occurrences.size(), runs));
            }
        });

        found.sort(Comparator.comparingInt(BlockPattern::occurrences).reversed());
        return List.copyOf(found);
    }

    static String normalise(String reason) {
        return reason.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
    }
}
