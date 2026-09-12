package com.flowops.nodepipeline.application.port;

import com.flowops.nodepipeline.domain.NodeVerdict;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PipelineJournalPort {
    UUID openRun(Instant windowFrom, Instant windowTo, String signature, AiMode aiMode, Instant startedAt);

    void recordVolumes(UUID runId, int nodesRead, int nodesInWindow, int jobsRead);

    /**
     * Records one row per decision. {@code modelId} and {@code promptVersion} name the model that
     * was configured for the run, whether or not it was asked anything; the per-row {@code
     * aiOutcome} on the verdict says whether it actually moved that decision.
     */
    void recordDecisions(UUID runId, List<NodeVerdict> verdicts, Instant at, String modelId, String promptVersion);

    void recordComparedDecisions(UUID runId, List<Comparison> comparisons, Instant at);

    record Comparison(
            NodeVerdict deterministic, NodeVerdict withAi, boolean aiFailed, String modelId, String promptVersion) {}

    void recordJobDecisions(UUID runId, List<com.flowops.nodepipeline.domain.job.JobVerdict> verdicts, Instant at);

    void recordDiscoveries(
            UUID runId,
            List<com.flowops.nodepipeline.domain.discovery.StepKind> kinds,
            List<com.flowops.nodepipeline.domain.discovery.DiscoveredProcess> processes,
            Instant at);

    void recordStuck(UUID runId, List<StuckItem> stuck);

    void closeRun(UUID runId, String reachedStage, String failure, Instant finishedAt);

    enum AiMode {
        OFF,

        ON,

        COMPARE
    }

    record StuckItem(String itemId, String itemKind, String lastStage, String reason) {}
}
