package com.flowops.nodepipeline.domain.notify;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record NudgeHistory(Set<String> nodesAlreadyNudged, List<PriorNudge> priorNudges) {
    public NudgeHistory {
        nodesAlreadyNudged = nodesAlreadyNudged == null ? Set.of() : Set.copyOf(nodesAlreadyNudged);
        priorNudges = priorNudges == null ? List.of() : List.copyOf(priorNudges);
    }

    public record PriorNudge(UUID person, String templateId) {}

    public static NudgeHistory none() {
        return new NudgeHistory(Set.of(), List.of());
    }

    public int repeatsFor(UUID person, String templateId) {
        if (person == null || templateId == null) {
            return 0;
        }
        return (int) priorNudges.stream()
                .filter(prior -> person.equals(prior.person()) && templateId.equals(prior.templateId()))
                .count();
    }
}
