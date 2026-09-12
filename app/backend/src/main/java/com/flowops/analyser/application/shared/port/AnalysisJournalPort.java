package com.flowops.analyser.application.shared.port;

import com.flowops.analyser.domain.Report;
import java.time.Instant;
import java.util.UUID;

public interface AnalysisJournalPort {
    UUID open(Instant windowFrom, Instant windowTo, Instant startedAt);

    void record(UUID runId, Report report, String failure);

    void finish(UUID runId, Instant finishedAt);

    void fail(UUID runId, Instant finishedAt, String failure);
}
