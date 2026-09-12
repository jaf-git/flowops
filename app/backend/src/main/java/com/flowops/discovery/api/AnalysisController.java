package com.flowops.discovery.api;

import com.flowops.discovery.application.analysis.AnalysisStorePort;
import com.flowops.discovery.application.analysis.DismissProposalUseCase;
import com.flowops.discovery.application.analysis.RunAnalysis;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/discovery/analysis")
@Tag(name = "Discovery · analysis", description = "What the work graph shows about how the business actually runs.")
public class AnalysisController {
    private final RunAnalysis running;
    private final AnalysisStorePort store;
    private final DismissProposalUseCase dismissing;

    public AnalysisController(RunAnalysis running, AnalysisStorePort store, DismissProposalUseCase dismissing) {
        this.running = running;
        this.store = store;
        this.dismissing = dismissing;
    }

    @Operation(
            summary = "Run the pipeline over the last thirty days",
            description =
                    """
                    Six stages: observe, normalise, measure, detect, correlate, recommend. Each detector
                    is asked its question of one window that was read once, so twenty questions cost one
                    pass over the graph.

                    A detector that fails is logged and the run continues — one badly-written question
                    must not cost the owner every other answer.

                    The response says what it read as well as what it found. That distinction is the
                    difference between an owner concluding "we are fine" and "this is not running".

                    Requires DISCOVERY_CANVAS_VIEW: this reads the business rather than any individual's
                    work, which is why it is the owner's and a manager's rather than everybody's.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The run, with what it read and what it found."),
        @ApiResponse(responseCode = "403", description = "The caller does not hold DISCOVERY_CANVAS_VIEW.")
    })
    @PreAuthorize("hasAuthority('DISCOVERY_CANVAS_VIEW')")
    @PostMapping("/run")
    public ResponseEntity<RunAnalysis.AnalysisRun> run() {
        return ResponseEntity.ok(running.run());
    }

    @Operation(
            summary = "What the latest run found",
            description =
                    """
                    The latest run only. Showing several runs together would put last week's figure beside
                    this week's with nothing to say which was which, and a reader would take whichever
                    they saw first. History is kept so the page can say "this got worse"; it is not the
                    default view.

                    Ordered by sample size, because a finding resting on forty cases deserves to be read
                    before one resting on four.

                    Every finding carries its evidence: what it rests on, how many cases, and the brackets
                    it was drawn from. A finding that could not say those things is never stored.
                    """)
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Findings, strongest evidence first.")})
    @PreAuthorize("hasAuthority('DISCOVERY_CANVAS_VIEW')")
    @GetMapping("/findings")
    public ResponseEntity<List<AnalysisStorePort.StoredFinding>> findings() {
        return ResponseEntity.ok(store.latestFindings());
    }

    @Operation(
            summary = "The latest run",
            description =
                    """
                    What the last pass looked at and how far it got, whether or not the caller started it.

                    The page needs this to be honest about itself. Without it the strip could only report
                    a run this session began, so a page showing a previous run's findings displayed
                    "0 brackets read" beside them — claiming to have read nothing while presenting
                    conclusions.
                    """)
    @ApiResponses({@ApiResponse(responseCode = "200", description = "The latest run, or nothing if none has run.")})
    @PreAuthorize("hasAuthority('DISCOVERY_CANVAS_VIEW')")
    @GetMapping("/run/latest")
    public ResponseEntity<AnalysisStorePort.StoredRun> latestRun() {
        return store.latestRun().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent()
                .build());
    }

    @Operation(
            summary = "What the latest run proposes",
            description =
                    """
                    The Recommend stage's output: a proposed action with the evidence it rests on, and a
                    button a person presses.

                    Nothing here has been done. The pipeline never writes a template, a classification or
                    a dependency - it notices and it suggests, and the decision stays with whoever reads
                    this. That is why a proposal carries no "applied" state: there is no state in which
                    the system did the thing.

                    Ordered by confidence, then by evidence. A proposal the pipeline has argued against
                    sinks to the bottom rather than being hidden, because the pattern under it is real and
                    a person may still want it - UNDERMINED says the evidence is thin in a specific,
                    fixable way, not that the idea is wrong.

                    Acted-on and dismissed proposals are excluded. A list that kept them would be a log,
                    and what somebody opens this for is a queue.
                    """)
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Open proposals, strongest first.")})
    @PreAuthorize("hasAuthority('DISCOVERY_CANVAS_VIEW')")
    @GetMapping("/recommendations")
    public ResponseEntity<List<AnalysisStorePort.StoredRecommendation>> recommendations() {
        return ResponseEntity.ok(store.latestRecommendations());
    }

    @Operation(
            summary = "Put a proposal aside",
            description =
                    """
                    The half of the queue that keeps the other half worth reading.

                    The pipeline proposes on every run. A workspace that has already considered and
                    rejected four suggestions meets those four again each week, above the one that is
                    new - and a list nobody can clear stops being read at all.

                    THIS IS NOT HIDING. The row keeps its evidence, its wording and its confidence, and
                    gains the fact that somebody looked at it and said no, with their name on it so they
                    can be asked why. What changes is that it stops being asked about.

                    Refused if somebody already decided it, rather than treated as idempotent: a
                    proposal that was written down as a process an hour ago is not one to put aside, and
                    saying nothing would let a person believe they had undone something.

                    Writes nothing outside this zone, which is why it is here and its sibling is not.
                    Write-it-down authors a process in the application's library and is a crossing; this
                    records a decision about an observation, on a row this zone owns.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Put aside."),
        @ApiResponse(responseCode = "403", description = "The caller does not hold DISCOVERY_CANVAS_VIEW."),
        @ApiResponse(responseCode = "409", description = "RECOMMENDATION_ALREADY_DECIDED."),
        @ApiResponse(responseCode = "422", description = "UNKNOWN_RECOMMENDATION.")
    })
    @PreAuthorize("hasAuthority('DISCOVERY_CANVAS_VIEW')")
    @PostMapping("/recommendations/{recommendationId}/dismiss")
    public ResponseEntity<Void> dismiss(@PathVariable UUID recommendationId) {
        dismissing.execute(recommendationId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Which findings mention this bracket",
            description =
                    """
                    The canvas's question, and the reverse direction of the same index the pipeline page
                    reads forwards. Select a node and this answers "why is this one coloured?".

                    Without it the drawer and the canvas would be two products that happen to share a
                    database, and the only way to answer that question would be to re-run every detector.
                    """)
    @ApiResponses({@ApiResponse(responseCode = "200", description = "The findings that name this bracket.")})
    @PreAuthorize("hasAuthority('DISCOVERY_CANVAS_VIEW')")
    @GetMapping("/findings/touching/{bracketId}")
    public ResponseEntity<List<AnalysisStorePort.StoredFinding>> touching(@PathVariable UUID bracketId) {
        return ResponseEntity.ok(store.findingsTouching(bracketId));
    }

    @Operation(
            summary = "Which brackets a finding was drawn from",
            description =
                    """
                    Read forwards: select a finding and highlight exactly these on the canvas. Volt's rule
                    already fits — nodes are white by default and colour appears only where there is a
                    status worth finding, so a healthy graph is almost entirely monochrome and a
                    highlighted finding is unmissable.
                    """)
    @ApiResponses({@ApiResponse(responseCode = "200", description = "The brackets behind this finding.")})
    @PreAuthorize("hasAuthority('DISCOVERY_CANVAS_VIEW')")
    @GetMapping("/findings/{findingId}/subjects")
    public ResponseEntity<List<UUID>> subjects(@PathVariable UUID findingId) {
        return ResponseEntity.ok(store.findingsOf(findingId));
    }
}
