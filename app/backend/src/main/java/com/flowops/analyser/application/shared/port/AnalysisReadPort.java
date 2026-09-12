package com.flowops.analyser.application.shared.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnalysisReadPort {
    Optional<RunView> latest();

    record RunView(
            UUID runId,
            Instant windowFrom,
            Instant windowTo,
            Instant startedAt,
            Instant finishedAt,
            String failure,
            List<AnalyserView> analysers) {}

    record AnalyserView(
            String analyser,
            int itemsRead,
            int findingsCount,
            String failure,
            List<AbsenceView> absences,
            List<CleanView> clean,
            List<PreconditionView> preconditions) {}

    record AbsenceView(String what, String detail, boolean blocking) {}

    record CleanView(String what, String detail) {}

    record PreconditionView(String needed, String had, boolean met, String remedy) {}
}
