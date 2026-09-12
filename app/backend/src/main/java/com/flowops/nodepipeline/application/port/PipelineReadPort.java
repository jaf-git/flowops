package com.flowops.nodepipeline.application.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PipelineReadPort {
    java.util.Optional<RunRecord> latestRun();

    List<StageTally> stagesOf(UUID runId);

    List<StuckRow> stuckIn(UUID runId);

    List<RunRecord> history(int limit);

    List<ItemMovement> movementsBetween(UUID before, UUID after);

    List<FindingRow> findingsIn(UUID runId, String findingKind, int limit);

    List<DecisionSource> sourcesOf(UUID decisionId);

    java.util.Optional<Comparison> comparisonOf(UUID runId);

    record Comparison(
            UUID runId,
            String aiMode,
            int compared,
            int agreed,
            int raised,
            int lowered,
            int changed,
            int failed,
            String modelId,
            String promptVersion) {}

    record RunRecord(
            UUID id,
            Instant windowFrom,
            Instant windowTo,
            Instant startedAt,
            Instant finishedAt,
            String reachedStage,
            String signature,
            String aiMode,
            int nodesRead,
            int nodesInWindow,
            int jobsRead,
            String failure) {}

    record StageTally(String stage, String outcome, int count) {}

    record ItemMovement(
            String itemId,
            String itemKind,
            String beforeStage,
            String beforeReason,
            String afterStage,
            String afterReason) {}

    record StuckRow(String itemId, String itemKind, String lastStage, String reason, int count) {}

    record FindingRow(
            UUID id, String findingKind, String subject, String outcome, Double score, String reason, String decided) {}

    record DecisionSource(String kind, UUID id, String label, String detail, UUID conversationId, UUID jobId) {}
}
