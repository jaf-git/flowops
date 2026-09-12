package com.flowops.nodepipeline.domain.discovery;

import java.util.List;
import java.util.Set;

public record StepKind(
        String id,
        String workType,
        String outputType,
        String conversation,
        List<String> nodeIds,
        Set<String> jobIds,
        List<String> words,
        double cohesion,
        double certainty,
        String activity,
        String activityName) {
    public StepKind {
        nodeIds = nodeIds == null ? List.of() : List.copyOf(nodeIds);
        jobIds = jobIds == null ? Set.of() : Set.copyOf(jobIds);
        words = words == null ? List.of() : List.copyOf(words);
    }

    public StepKind(
            String id,
            String workType,
            String outputType,
            String conversation,
            List<String> nodeIds,
            Set<String> jobIds,
            List<String> words,
            double cohesion,
            double certainty) {
        this(id, workType, outputType, conversation, nodeIds, jobIds, words, cohesion, certainty, null, null);
    }

    public boolean isSubprocess() {
        return conversation != null;
    }

    public boolean namesAnActivity() {
        return activity != null && !activity.isBlank();
    }

    public String bestName() {
        return activityName == null || activityName.isBlank() ? workType : activityName;
    }
}
