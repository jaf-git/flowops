package com.flowops.discovery.application.shared.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface VocabularyReadPort {
    Familiarity familiarityOf(String workType);

    List<FirstUse> workTypesFirstUsedSince(Instant since);

    List<FirstUse> counterpartiesAndProjectsFirstSeenSince(Instant since);

    List<MergeSuggestion> typesWorthMerging(int rareBelow);

    record Familiarity(String workType, boolean neverUsedBefore, List<String> closestExisting) {}

    record FirstUse(String kind, String name, Instant at, UUID firstUsedBy, String firstUsedByName) {}

    record MergeSuggestion(String one, int oneUses, String other, int otherUses, String sharedPrefix) {}
}
