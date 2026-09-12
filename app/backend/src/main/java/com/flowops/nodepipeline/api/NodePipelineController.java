package com.flowops.nodepipeline.api;

import com.flowops.nodepipeline.application.RunNodePipeline;
import com.flowops.nodepipeline.application.port.PipelineJournalPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/node-pipeline")
@Tag(
        name = "Node pipeline",
        description = "Does this piece of work match a task template we already have? Six stages over the node "
                + "graph, recorded so any decision is explainable from its row alone.")
public class NodePipelineController {
    private final RunNodePipeline runPipeline;

    public NodePipelineController(RunNodePipeline runPipeline) {
        this.runPipeline = runPipeline;
    }

    @GetMapping("/runs/preview")
    @PreAuthorize("hasAuthority('PIPELINE_RUN_VIEW')")
    @Operation(
            summary = "How much work a window holds",
            description = "NODEPIPE-RUN-01. Reads nothing and decides nothing: it counts the nodes a run "
                    + "over this window would consider, and says whether the 200-node cap would bite. "
                    + "Requires PIPELINE_RUN_VIEW, because it is a read.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The count. Zero is a real answer."),
        @ApiResponse(responseCode = "401", description = "No session."),
        @ApiResponse(responseCode = "403", description = "The caller does not hold PIPELINE_RUN_VIEW."),
        @ApiResponse(
                responseCode = "400",
                description = "WINDOW_RUNS_BACKWARDS: the window ends before it begins, so the zero "
                        + "it would otherwise answer would be a confident nonsense.")
    })
    public ResponseEntity<RunNodePipeline.WindowPreview> preview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(from == null || to == null ? runPipeline.preview() : runPipeline.preview(from, to));
    }

    @PostMapping("/runs")
    @PreAuthorize("hasAuthority('PIPELINE_RUN_START')")
    @Operation(
            summary = "Run the node matcher over a window",
            description = "NODEPIPE-RUN-01. Reads at most 200 nodes and the approved template library, scores "
                    + "each node, and writes one decision row per node including every abstention — "
                    + "precision is measurable from what the pipeline said, recall only from what it did "
                    + "not. Writes nothing else: no template is created and nobody is notified. "
                    + "Requires PIPELINE_RUN_START.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The run finished. Abstaining on every node is a result."),
        @ApiResponse(responseCode = "401", description = "No session."),
        @ApiResponse(responseCode = "403", description = "The caller does not hold PIPELINE_RUN_START."),
        @ApiResponse(
                responseCode = "400",
                description = "WINDOW_RUNS_BACKWARDS: the window ends before it begins. Refused by "
                        + "the use case before a run is opened, so nothing is written.")
    })
    public ResponseEntity<RunResponse> run(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false, defaultValue = "OFF") PipelineJournalPort.AiMode aiMode) {
        RunNodePipeline.RunSummary summary =
                from == null || to == null ? runPipeline.run(aiMode) : runPipeline.run(from, to, aiMode);

        return ResponseEntity.ok(new RunResponse(
                summary.runId(),
                summary.signature(),
                summary.windowFrom(),
                summary.windowTo(),
                summary.nodesRead(),
                summary.templatesConsidered(),
                summary.tiers(),
                summary.jobsRead(),
                summary.processesConsidered(),
                summary.jobTiers(),
                summary.discoveryCandidates(),
                summary.stepKindsFound(),
                summary.processesDiscovered(),
                summary.excludedFromDiscovery(),
                summary.nodesInWindow()));
    }

    public record RunResponse(
            UUID runId,
            String signature,
            Instant windowFrom,
            Instant windowTo,
            int nodesRead,
            int templatesConsidered,
            Map<String, Integer> tiers,
            int jobsRead,
            int processesConsidered,
            Map<String, Integer> jobTiers,
            int discoveryCandidates,
            int stepKindsFound,
            int processesDiscovered,
            int excludedFromDiscovery,
            int nodesInWindow) {}
}
