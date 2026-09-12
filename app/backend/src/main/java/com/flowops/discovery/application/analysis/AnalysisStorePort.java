package com.flowops.discovery.application.analysis;

import com.flowops.discovery.domain.analysis.Finding;
import com.flowops.discovery.domain.analysis.Recommendation;
import com.flowops.discovery.domain.analysis.Stage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AnalysisStorePort {
    void openRun(UUID runId, Instant from, Instant to, Instant startedAt, int bracketsRead, int waitsRead);

    UUID record(UUID runId, Finding finding, Stage stage, Phrasing.Phrased phrased, Instant at);

    void finishRun(UUID runId, Instant finishedAt, Stage reached);

    void propose(UUID runId, UUID findingId, Recommendation recommendation, Instant at);

    java.util.Optional<StoredRun> latestRun();

    List<StoredRecommendation> latestRecommendations();

    java.util.Optional<ProposedAction> proposal(UUID recommendationId);

    boolean claim(UUID recommendationId, UUID by, Instant at);

    void recordProduced(UUID recommendationId, UUID processTemplateId);

    boolean dismiss(UUID recommendationId, UUID by, Instant at);

    record ProposedAction(
            UUID id,
            UUID runId,
            UUID findingId,
            String kind,
            String subjectKind,
            String subjectKey,
            String headline,
            int sampleSize,
            boolean decided) {}

    List<StoredFinding> latestFindings();

    List<StoredFinding> findingsTouching(UUID bracketId);

    List<UUID> findingsOf(UUID findingId);

    record StoredFinding(
            UUID id,
            UUID runId,
            String detector,
            String stage,
            String subjectKind,
            String subjectKey,
            String headline,
            String phrasing,
            String phrasedBy,
            int sampleSize,
            java.math.BigDecimal measure,
            String unit,
            Instant createdAt) {}

    record StoredRecommendation(
            UUID id,
            UUID runId,
            UUID findingId,
            String kind,
            String headline,
            String detail,
            String confidence,
            int sampleSize,
            Instant createdAt) {}

    record StoredRun(
            UUID id,
            Instant from,
            Instant to,
            int bracketsRead,
            int waitsRead,
            String reached,
            Instant startedAt,
            Instant finishedAt) {}
}
