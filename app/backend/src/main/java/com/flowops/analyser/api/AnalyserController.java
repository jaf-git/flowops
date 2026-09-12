package com.flowops.analyser.api;

import com.flowops.analyser.api.dto.AnalysisRunResponse;
import com.flowops.analyser.api.dto.FindingEvidenceResponse;
import com.flowops.analyser.api.dto.FindingQueueResponse;
import com.flowops.analyser.application.dismissfinding.DismissFindingUseCase;
import com.flowops.analyser.application.run.RunAnalysisUseCase;
import com.flowops.analyser.application.shared.port.AnalysisReadPort;
import com.flowops.analyser.application.viewevidence.ViewFindingEvidenceUseCase;
import com.flowops.analyser.application.viewfindings.ViewFindingsUseCase;
import com.flowops.shared.web.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analysis")
@Tag(
        name = "Analysis",
        description = "Nine small analysers over one snapshot of the work graph, each declaring what it could not see.")
public class AnalyserController {
    private final RunAnalysisUseCase runAnalysis;
    private final AnalysisReadPort analyses;
    private final ViewFindingsUseCase viewFindings;
    private final DismissFindingUseCase dismissFinding;
    private final ViewFindingEvidenceUseCase viewEvidence;

    public AnalyserController(
            RunAnalysisUseCase runAnalysis,
            AnalysisReadPort analyses,
            ViewFindingsUseCase viewFindings,
            DismissFindingUseCase dismissFinding,
            ViewFindingEvidenceUseCase viewEvidence) {
        this.runAnalysis = runAnalysis;
        this.analyses = analyses;
        this.viewFindings = viewFindings;
        this.dismissFinding = dismissFinding;
        this.viewEvidence = viewEvidence;
    }

    @Operation(
            summary = "Look at the graph, and let every analyser answer",
            description =
                    """
                    ANALYSER-RUN-01. Requires PIPELINE_RUN_START.

                    One run, one window, one snapshot, many analysers. Every analyser reads the same
                    snapshot, so two of them cannot disagree from different eras — the defect class that
                    has cost this project five sessions.

                    The window defaults to 90 days rather than 30, and the number comes from a
                    measurement: the corpus is 76% future-dated, so a 30-day window holds 83 of 348
                    nodes. A default that hid three-quarters of the graph would make every honest empty
                    answer look like a fault. The window used is always on the response.

                    One broken analyser does not take the others down. Its failure is recorded against
                    it and the run continues, because a run that ended on the first exception is the
                    sequential-emptying failure this whole subsystem was designed against.

                    Every analyser reports what it read, even when it found nothing. Silence over 400
                    brackets and silence over 0 brackets are different facts.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "What each analyser read, found, and could not see"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold PIPELINE_RUN_START",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "A window of zero days or fewer holds nothing to analyse",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/runs")
    @PreAuthorize("hasAuthority('PIPELINE_RUN_START')")
    public ResponseEntity<AnalysisRunResponse> run(@RequestParam(required = false) Integer windowDays) {
        RunAnalysisUseCase.Summary summary = runAnalysis.execute(new RunAnalysisUseCase.RunAnalysis(windowDays));
        return ResponseEntity.ok(AnalysisRunResponse.of(summary));
    }

    @Operation(
            summary = "What the last run said, analyser by analyser",
            description =
                    """
                    ANALYSER-RUN-01. Requires PIPELINE_RUN_START.

                    Four things per analyser, and the fourth is the one that matters: what it read, what
                    it found, what it needed, and what it could not see. That last column is the
                    diagnostic that would have replaced four investigations with one screen.

                    204 where no analysis has ever been run — which is a different answer from a run
                    that found nothing, and the caller is owed the difference.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The most recent run"),
        @ApiResponse(responseCode = "204", description = "No analysis has been run yet", content = @Content),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold PIPELINE_RUN_START",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/runs/latest")
    @PreAuthorize("hasAuthority('PIPELINE_RUN_START')")
    public ResponseEntity<AnalysisRunResponse> latest() {
        return analyses.latest()
                .map(view -> ResponseEntity.ok(AnalysisRunResponse.of(view)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(
            summary = "The queue: what to do about your business, worst first",
            description =
                    """
                    ANALYSER-VIEW-FINDINGS-01. Requires PIPELINE_RUN_START.

                    A queue, not a report. A report is read once; a queue is worked.

                    Grouped by the five categories and sorted inside each, never across (A14): reach
                    counts templates for one analyser and brackets for another, so a single global
                    ordering weighs 41 of one against 153 of the other and is arbitrary while looking
                    authoritative.

                    Every item carries the one line that explains its place — "High because it touches
                    41 of 63 and is worse than last run". An opaque rank is the first thing an owner
                    dismisses, and then the second.

                    A finding shown past its grace and not acted on fades, floored so it never
                    disappears, and says how many times it has been shown. Worse than when you last
                    looked resets that, because a finding that has just got worse must not arrive
                    already faded.

                    Dismissed findings are counted in `standing` and left out of the groups.

                    204 where no analysis has ever been run.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The queue as it stands"),
        @ApiResponse(responseCode = "204", description = "No analysis has been run yet", content = @Content),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold PIPELINE_RUN_START",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/findings")
    @PreAuthorize("hasAuthority('PIPELINE_RUN_START')")
    public ResponseEntity<FindingQueueResponse> findings() {
        return viewFindings
                .execute()
                .map(queue -> ResponseEntity.ok(FindingQueueResponse.of(queue)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(
            summary = "What this finding rests on, with every reference resolved",
            description =
                    """
                    ANALYSER-VIEW-FINDINGS-01. Requires PIPELINE_RUN_START.

                    The queue says what was found; this says why anybody should believe it. A finding
                    reading "Copy and caption happens often enough to be worth writing down" is an
                    assertion until the marks behind it can be read — their messages, what people
                    called the work, its checklist, who marked it, who did it, and in which department.

                    Every node carries its engagement and its message so a reader can be taken to the
                    graph and to the conversation, and carries the words themselves so that usually
                    they do not have to go.

                    A person appears here as provenance and never as a figure. Nothing aggregates
                    across people and no field could be summed into one — DECISION-APPROVAL-SCORE-01.

                    A finding whose subjects were all engagements has no nodes, and three empty lists
                    are an ordinary answer. An identifier naming nothing is a 404 rather than three
                    empty lists, because "this finding rests on nothing" and "there is no such
                    finding" must not read the same.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The evidence, resolved"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold PIPELINE_RUN_START",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "NOT_FOUND: no finding of any run carries that identifier",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/findings/{id}/evidence")
    @PreAuthorize("hasAuthority('PIPELINE_RUN_START')")
    public ResponseEntity<FindingEvidenceResponse> evidence(@PathVariable UUID id) {
        return ResponseEntity.ok(FindingEvidenceResponse.of(viewEvidence.execute(id)));
    }

    @Operation(
            summary = "Not now — and it survives the next run",
            description =
                    """
                    ANALYSER-DISMISS-FINDING-01. Requires PIPELINE_RUN_START.

                    The analysers re-derive the same finding from every snapshot, so without a record of
                    the judgement the queue would present it again the next morning. A queue that
                    re-asks a question you have already answered is a queue people stop opening.

                    It comes back on exactly two conditions, neither of them a timer (A3): it reaches
                    half again as far as it did when you said no, or it stops being the same kind of
                    problem. It returns marked WORSENING rather than NEW, so the page can say you
                    dismissed this at 41 and it is 62 now.

                    The identifier is the finding row on the run you were looking at, so what is
                    dismissed is the sighting you actually saw.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Recorded"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold PIPELINE_RUN_START",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "NOT_FOUND: no finding of any run carries that identifier",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/findings/{id}/dismiss")
    @PreAuthorize("hasAuthority('PIPELINE_RUN_START')")
    public ResponseEntity<Void> dismiss(@PathVariable UUID id) {
        dismissFinding.execute(new DismissFindingUseCase.Dismiss(id));
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(DismissFindingUseCase.NoSuchFinding.class)
    public ResponseEntity<ErrorResponse> noSuchFinding(DismissFindingUseCase.NoSuchFinding absent) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of("NOT_FOUND", absent.getMessage()));
    }
}
