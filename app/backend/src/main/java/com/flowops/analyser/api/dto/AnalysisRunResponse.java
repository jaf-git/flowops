package com.flowops.analyser.api.dto;

import com.flowops.analyser.application.run.RunAnalysisUseCase;
import com.flowops.analyser.application.shared.port.AnalysisReadPort;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AnalysisRunResponse(
        UUID runId,
        Instant windowFrom,
        Instant windowTo,
        @Schema(description = "Total across every analyser") int findingsTotal,
        @Schema(description = "The run itself failing, as opposed to one analyser failing") String failure,
        List<Analyser> analysers) {
    public record Analyser(
            String id,
            int itemsRead,
            int findings,
            @Schema(description = "This analyser's own exception. Null where it ran.") String failure,
            List<Absence> absences,
            List<Clean> clean,
            List<Precondition> preconditions) {}

    public record Absence(String what, String detail, boolean blocking) {}

    public record Clean(String what, String detail) {}

    public record Precondition(String needed, String had, boolean met, String remedy) {}

    public static AnalysisRunResponse of(RunAnalysisUseCase.Summary summary) {
        return new AnalysisRunResponse(
                summary.runId(),
                summary.windowFrom(),
                summary.windowTo(),
                summary.findingCount(),
                null,
                summary.reports().stream()
                        .map(report -> new Analyser(
                                report.analyser(),
                                report.read(),
                                report.findings(),
                                report.failure(),
                                List.of(),
                                List.of(),
                                List.of()))
                        .toList());
    }

    public static AnalysisRunResponse of(AnalysisReadPort.RunView view) {
        return new AnalysisRunResponse(
                view.runId(),
                view.windowFrom(),
                view.windowTo(),
                view.analysers().stream()
                        .mapToInt(AnalysisReadPort.AnalyserView::findingsCount)
                        .sum(),
                view.failure(),
                view.analysers().stream()
                        .map(analyser -> new Analyser(
                                analyser.analyser(),
                                analyser.itemsRead(),
                                analyser.findingsCount(),
                                analyser.failure(),
                                analyser.absences().stream()
                                        .map(absence ->
                                                new Absence(absence.what(), absence.detail(), absence.blocking()))
                                        .toList(),
                                analyser.clean().stream()
                                        .map(clean -> new Clean(clean.what(), clean.detail()))
                                        .toList(),
                                analyser.preconditions().stream()
                                        .map(precondition -> new Precondition(
                                                precondition.needed(),
                                                precondition.had(),
                                                precondition.met(),
                                                precondition.remedy()))
                                        .toList()))
                        .toList());
    }
}
