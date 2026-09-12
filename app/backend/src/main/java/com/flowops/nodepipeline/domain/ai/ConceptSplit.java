package com.flowops.nodepipeline.domain.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ConceptSplit {
    private static final Map<String, Set<String>> PLAUSIBLE_FOR_WORK_TYPE = Map.of(
            "CONTENT", Set.of("CAPTIONS", "BLOG", "NEWSLETTER", "COPY"),
            "WRITER", Set.of("CAPTIONS", "BLOG", "NEWSLETTER", "COPY"),
            "DESIGN", Set.of("LAYOUT", "BRAND", "ARTWORK", "DECK"),
            "PHOTO", Set.of("SHOOT", "RETOUCH", "PACKSHOT"),
            "VIDEO", Set.of("SHOOT", "EDIT", "TEASER"),
            "ADS", Set.of("CAMPAIGN", "VARIANTS", "BUDGET"),
            "REPORTING", Set.of("REPORT", "RECAP", "NUMBERS"),
            "SCHEDULING", Set.of("SCHEDULE", "QUEUE"),
            "CLIENT_INTAKE", Set.of("BRIEF", "ONBOARDING", "REQUIREMENTS"),
            "COORDINATION", Set.of("PROOF", "RETRO", "HANDOVER"));

    private ConceptSplit() {}

    public static Set<String> conceptsFor(String workType) {
        return workType == null
                ? Set.of()
                : PLAUSIBLE_FOR_WORK_TYPE.getOrDefault(workType.toUpperCase(java.util.Locale.ROOT), Set.of());
    }

    public static final String WHOLE = "";

    public static boolean plausibleFor(String workType, String concept) {
        if (workType == null || concept == null) {
            return false;
        }
        return PLAUSIBLE_FOR_WORK_TYPE
                .getOrDefault(workType.toUpperCase(java.util.Locale.ROOT), Set.of())
                .contains(concept.toUpperCase(java.util.Locale.ROOT));
    }

    public static Map<String, List<String>> split(
            Map<String, String> conceptOfNode, String workType, int minimumNodes) {
        Map<String, List<String>> byConcept = new LinkedHashMap<>();
        for (Map.Entry<String, String> labelled : conceptOfNode.entrySet()) {
            String concept = labelled.getValue();

            if (concept != null && plausibleFor(workType, concept)) {
                byConcept
                        .computeIfAbsent(concept.toUpperCase(java.util.Locale.ROOT), key -> new java.util.ArrayList<>())
                        .add(labelled.getKey());
            }
        }

        List<String> substantial = byConcept.entrySet().stream()
                .filter(e -> e.getValue().size() >= minimumNodes)
                .map(Map.Entry::getKey)
                .toList();

        if (substantial.size() < 2) {
            return Map.of("", List.copyOf(conceptOfNode.keySet()));
        }

        java.util.Comparator<String> bySize = java.util.Comparator.<String>comparingInt(
                        concept -> byConcept.get(concept).size())
                .thenComparing(java.util.Comparator.<String>reverseOrder());
        String largest = substantial.stream().max(bySize).orElseThrow();

        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (String concept : substantial) {
            groups.put(concept, new java.util.ArrayList<>(byConcept.get(concept)));
        }

        for (String nodeId : conceptOfNode.keySet()) {
            boolean placed = groups.values().stream().anyMatch(members -> members.contains(nodeId));
            if (!placed) {
                groups.get(largest).add(nodeId);
            }
        }
        return groups;
    }
}
