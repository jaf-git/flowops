package com.flowops.aiinsight.application;

import com.flowops.aiinsight.domain.ClosureCoverage;
import com.flowops.aiinsight.domain.InsightAction;
import com.flowops.aiinsight.domain.InsightIdentity;
import com.flowops.aiinsight.domain.SubjectType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ViewInsightsUseCase {
    List<Insight> forSubject(SubjectType subjectType, UUID subjectId);

    record Insight(
            InsightIdentity identity, String subjectName, Evidence evidence, Detail detail, InsightAction action) {
        public boolean isActionable() {
            return action != null;
        }
    }

    record Evidence(
            int sampleSize,
            int population,
            ClosureCoverage coverage,
            Instant windowFrom,
            Instant windowTo,
            List<UUID> instances) {
        public static Evidence of(int sampleSize, int population, Instant from, Instant to, List<UUID> instances) {
            return new Evidence(sampleSize, population, null, from, to, List.copyOf(instances));
        }

        public static Evidence of(
                int sampleSize,
                int population,
                ClosureCoverage coverage,
                Instant from,
                Instant to,
                List<UUID> instances) {
            return new Evidence(sampleSize, population, coverage, from, to, List.copyOf(instances));
        }
    }

    sealed interface Detail {
        record MissingStep(String title, String afterStep, String beforeStep, int position) implements Detail {}

        record SlowStep(String title, Phases medianPhases) implements Detail {}

        record BlockPattern(String stepTitle, String reason) implements Detail {}

        record FalseDependency(String dependentTitle, String dependsOnTitle) implements Detail {}

        record UnusedTemplate(int daysSinceLastUse, int timesUsed, int windowDays, Integer expectedIntervalDays)
                implements Detail {}

        record EstimateDivergence(
                Long estimatedMs, long medianWorkMs, long fastestMiddleMs, long slowestMiddleMs, boolean spreadIsWide)
                implements Detail {}
    }

    record Phases(long workMs, long blockedMs, long waitingMs, long reviewMs) {}
}
