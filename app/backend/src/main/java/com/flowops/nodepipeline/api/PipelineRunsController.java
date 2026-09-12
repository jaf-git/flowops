package com.flowops.nodepipeline.api;

import com.flowops.nodepipeline.application.port.PipelineReadPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/node-pipeline/runs")
@Tag(
        name = "Node pipeline runs",
        description = "What the last run decided, what it dropped, and why. Abstentions are most of it, "
                + "and that is the product working.")
public class PipelineRunsController {
    private static final int DEFAULT_FINDING_LIMIT = 50;

    private final PipelineReadPort runs;

    public PipelineRunsController(PipelineReadPort runs) {
        this.runs = runs;
    }

    private static final int DEFAULT_HISTORY_LIMIT = 20;

    @GetMapping
    @PreAuthorize("hasAuthority('PIPELINE_RUN_VIEW')")
    @Operation(
            summary = "Past runs, newest first",
            description = "NODEPIPE-RUN-01. Version, signature, window, timestamps and per-run counts. "
                    + "Two of these can be diffed. Requires PIPELINE_RUN_VIEW.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The runs. An empty list means none has ever run."),
        @ApiResponse(responseCode = "401", description = "No session."),
        @ApiResponse(responseCode = "403", description = "The caller does not hold PIPELINE_RUN_VIEW.")
    })
    public ResponseEntity<List<PipelineReadPort.RunRecord>> history(
            @RequestParam(required = false, defaultValue = "" + DEFAULT_HISTORY_LIMIT) int limit) {
        return ResponseEntity.ok(runs.history(Math.max(1, Math.min(limit, 100))));
    }

    @GetMapping("/{before}/diff/{after}")
    @PreAuthorize("hasAuthority('PIPELINE_RUN_VIEW')")
    @Operation(
            summary = "What moved between two runs",
            description = "NODEPIPE-RUN-01. One row per item whose stage or reason differs, plus items "
                    + "present in only one of the two. Items identical in both are omitted. A null "
                    + "stage means that run recorded nothing for the item — it finished, or it was "
                    + "never read. Requires PIPELINE_RUN_VIEW.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The movements. Empty means the two runs agree."),
        @ApiResponse(responseCode = "401", description = "No session."),
        @ApiResponse(responseCode = "403", description = "The caller does not hold PIPELINE_RUN_VIEW.")
    })
    public ResponseEntity<List<PipelineReadPort.ItemMovement>> diff(
            @PathVariable UUID before, @PathVariable UUID after) {
        return ResponseEntity.ok(runs.movementsBetween(before, after));
    }

    @GetMapping("/latest")
    @PreAuthorize("hasAuthority('PIPELINE_RUN_VIEW')")
    @Operation(
            summary = "The last run, with its per-stage tallies",
            description = "NODEPIPE-VIEW-01. Returns the most recent node-pipeline run, its configuration "
                    + "signature, what it read, and how many items reached each outcome. Requires "
                    + "PIPELINE_RUN_VIEW.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The last run."),
        @ApiResponse(
                responseCode = "404",
                description = "The pipeline has never run — not the same as finding nothing."),
        @ApiResponse(responseCode = "401", description = "No session."),
        @ApiResponse(responseCode = "403", description = "The caller does not hold PIPELINE_RUN_VIEW.")
    })
    public ResponseEntity<LatestRunResponse> latest() {
        return runs.latestRun()
                .map(run -> ResponseEntity.ok(new LatestRunResponse(run, runs.stagesOf(run.id()))))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/latest/stuck")
    @PreAuthorize("hasAuthority('PIPELINE_RUN_VIEW')")
    @Operation(
            summary = "What the last run dropped, and why",
            description = "NODEPIPE-VIEW-02. The stuck ledger: every item that produced nothing, grouped by "
                    + "stage and reason, with one example each. This is the only read in the feature about "
                    + "things that did not happen, and the Pipeline screen's central claim rests on it. "
                    + "Requires PIPELINE_RUN_VIEW.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The drop list. Empty is a real answer."),
        @ApiResponse(responseCode = "404", description = "The pipeline has never run."),
        @ApiResponse(responseCode = "401", description = "No session."),
        @ApiResponse(responseCode = "403", description = "The caller does not hold PIPELINE_RUN_VIEW.")
    })
    public ResponseEntity<List<PipelineReadPort.StuckRow>> stuck() {
        return runs.latestRun()
                .map(run -> ResponseEntity.ok(runs.stuckIn(run.id())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/latest/findings")
    @PreAuthorize("hasAuthority('PIPELINE_RUN_VIEW')")
    @Operation(
            summary = "What the last run found",
            description = "NODEPIPE-VIEW-03. Findings of one kind, most certain first, carrying whatever a "
                    + "person already decided about each. Nothing here has been applied — every artefact "
                    + "this system proposes is a DRAFT somebody approves (ADR-015). Requires "
                    + "PIPELINE_RUN_VIEW.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The findings. Empty is a real answer."),
        @ApiResponse(responseCode = "404", description = "The pipeline has never run."),
        @ApiResponse(responseCode = "401", description = "No session."),
        @ApiResponse(responseCode = "403", description = "The caller does not hold PIPELINE_RUN_VIEW.")
    })
    public ResponseEntity<List<PipelineReadPort.FindingRow>> findings(
            @RequestParam(defaultValue = "NODE_MATCH") String kind, @RequestParam(required = false) Integer limit) {
        return runs.latestRun()
                .map(run -> ResponseEntity.ok(
                        runs.findingsIn(run.id(), kind, limit == null ? DEFAULT_FINDING_LIMIT : limit)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/decisions/{decisionId}/sources")
    @PreAuthorize("hasAuthority('PIPELINE_RUN_VIEW')")
    @Operation(
            summary = "Which marks and engagements a decision was made from",
            description = "NODEPIPE-VIEW-SOURCES-01. Every row the decision rested on, resolved to a name "
                    + "rather than an identifier, with the conversation a mark lives in so a reader can be "
                    + "taken to it. Empty where the decision names its subject directly, which is a JOB_MATCH. "
                    + "Requires PIPELINE_RUN_VIEW.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The sources. Empty is a real answer."),
        @ApiResponse(responseCode = "401", description = "No session."),
        @ApiResponse(responseCode = "403", description = "The caller does not hold PIPELINE_RUN_VIEW.")
    })
    public List<PipelineReadPort.DecisionSource> sources(@PathVariable UUID decisionId) {
        return runs.sourcesOf(decisionId);
    }

    @GetMapping("/{runId}/comparison")
    @PreAuthorize("hasAuthority('PIPELINE_RUN_VIEW')")
    @Operation(
            summary = "How the model and the rules differed on one run",
            description = "NODEPIPE-VIEW-COMPARISON-01. Five buckets over the nodes a COMPARE run scored "
                    + "twice: agreed, raised, lowered, changed and failed. `compared` is the denominator "
                    + "and the only cost figure recorded -- neither the number of model calls nor the "
                    + "time they took is stored, so neither is reported. `failed` is asked-and-unusable "
                    + "and is deliberately distinct from never-asked, which is what makes a silently "
                    + "degrading plug point visible. COMPARE doubles the work of every run it is on and "
                    + "wants an end date. Requires PIPELINE_RUN_VIEW.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The two paths, counted against each other."),
        @ApiResponse(
                responseCode = "204",
                description = "That run was not in COMPARE — a different answer from zeros."),
        @ApiResponse(responseCode = "401", description = "No session."),
        @ApiResponse(responseCode = "403", description = "The caller does not hold PIPELINE_RUN_VIEW.")
    })
    public ResponseEntity<PipelineReadPort.Comparison> comparison(@PathVariable java.util.UUID runId) {
        return runs.comparisonOf(runId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent()
                .build());
    }

    public record LatestRunResponse(PipelineReadPort.RunRecord run, List<PipelineReadPort.StageTally> stages) {}
}
