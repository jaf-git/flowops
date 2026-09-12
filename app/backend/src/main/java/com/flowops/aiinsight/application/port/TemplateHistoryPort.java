package com.flowops.aiinsight.application.port;

import com.flowops.aiinsight.domain.BlockPatternDetection.BlockOccurrence;
import com.flowops.aiinsight.domain.ClosureCoverage;
import com.flowops.aiinsight.domain.FalseDependencyDetection.EdgeObservation;
import com.flowops.aiinsight.domain.MissingStepDetection.AttachedStep;
import com.flowops.aiinsight.domain.SlowStepDetection.StepDuration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface TemplateHistoryPort {
    History of(UUID templateId);

    int MAX_STEPS_TO_LOOK = 20;

    record History(
            String templateName,
            int completedRuns,
            int runsNeverCompleted,
            ClosureCoverage coverage,
            List<AttachedStep> attachedSteps,
            List<StepDuration> stepDurations,
            List<BlockOccurrence> blocks,
            List<EdgeObservation> edgeObservations,
            Map<Integer, String> plannedTitlesByPosition,
            List<String> fingerprintParts,
            Instant firstCompletedAt,
            Instant lastCompletedAt) {
        public History withCoverageResolvedAt(int thresholdPercent) {
            return new History(
                    templateName,
                    completedRuns,
                    runsNeverCompleted,
                    ClosureCoverage.materialIn(completedRuns, completedRuns + runsNeverCompleted, thresholdPercent),
                    attachedSteps,
                    stepDurations,
                    blocks,
                    edgeObservations,
                    plannedTitlesByPosition,
                    fingerprintParts,
                    firstCompletedAt,
                    lastCompletedAt);
        }

        public String plannedStepBefore(int position) {
            return nearest(position, -1);
        }

        public String plannedStepAfter(int position) {
            return nearest(position, 1);
        }

        private String nearest(int position, int direction) {
            for (int away = 1; away <= MAX_STEPS_TO_LOOK; away++) {
                int at = position + direction * away;
                if (at < 0) {
                    return null;
                }
                String planned = plannedTitlesByPosition.get(at);
                if (planned != null) {
                    return planned;
                }
            }
            return null;
        }
    }
}
