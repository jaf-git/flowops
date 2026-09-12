package com.flowops.nodepipeline.application;

import java.time.Instant;
import java.util.List;

public interface DiscoverShapesUseCase {
    Shapes shapesIn(Instant from, Instant to);

    record Shapes(int read, int excluded, List<StepKindView> kinds, List<ProcessView> processes) {
        public Shapes {
            kinds = kinds == null ? List.of() : List.copyOf(kinds);
            processes = processes == null ? List.of() : List.copyOf(processes);
        }
    }

    record StepKindView(
            String id,
            String workType,
            boolean subprocess,
            List<String> nodeIds,
            List<String> jobIds,
            List<String> words,
            double cohesion,
            double certainty,
            String activity,
            String activityName) {
        public StepKindView(
                String id,
                String workType,
                boolean subprocess,
                List<String> nodeIds,
                List<String> jobIds,
                List<String> words,
                double cohesion,
                double certainty) {
            this(id, workType, subprocess, nodeIds, jobIds, words, cohesion, certainty, null, null);
        }
    }

    record ProcessView(
            List<String> steps,
            List<String> order,
            boolean orderReliable,
            double orderConfidence,
            List<String> jobIds,
            int runs,
            double certainty) {}
}
