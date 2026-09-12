package com.flowops.analyser.application.run;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface RunAnalysisUseCase {
    Summary execute(RunAnalysis command);

    record RunAnalysis(Integer windowDays) {}

    record Summary(UUID runId, Instant windowFrom, Instant windowTo, List<AnalyserOutcome> reports) {
        public int findingCount() {
            return reports.stream().mapToInt(AnalyserOutcome::findings).sum();
        }
    }

    record AnalyserOutcome(
            String analyser, int read, int findings, int absences, int unmetPreconditions, String failure) {
        public boolean blocked() {
            return failure != null || unmetPreconditions > 0;
        }
    }
}
