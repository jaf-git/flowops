package com.flowops.discovery.api;

import com.flowops.discovery.api.dto.BlockNodeRequest;
import com.flowops.discovery.api.dto.EnrichNodeRequest;
import com.flowops.discovery.api.dto.NodeEnrichmentResponse;
import com.flowops.discovery.api.dto.NodeProgressResponse;
import com.flowops.discovery.api.dto.NodeTrailResponse;
import com.flowops.discovery.api.dto.NudgeAnswerRequest;
import com.flowops.discovery.api.dto.NudgeResponse;
import com.flowops.discovery.api.dto.RecordOutputRequest;
import com.flowops.discovery.application.blocknode.BlockNodeUseCase;
import com.flowops.discovery.application.enrichnode.EnrichNodeUseCase;
import com.flowops.discovery.application.nodetrail.ViewNodeTrailUseCase;
import com.flowops.discovery.application.nudge.NudgeUseCase;
import com.flowops.discovery.application.recordoutput.RecordOutputUseCase;
import com.flowops.discovery.domain.enums.NudgeAnswer;
import com.flowops.discovery.domain.enums.OutputType;
import com.flowops.discovery.domain.enums.WaitingOn;
import com.flowops.discovery.domain.model.TrackId;
import com.flowops.discovery.domain.model.WorkNodeId;
import com.flowops.shared.web.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/discovery")
@Tag(
        name = "Discovery",
        description = "The work a business does, discovered from what its people said rather than described.")
public class NodeProgressController {
    private final RecordOutputUseCase recordOutputUseCase;
    private final BlockNodeUseCase blockNodeUseCase;
    private final NudgeUseCase nudgeUseCase;
    private final EnrichNodeUseCase enrichNodeUseCase;
    private final ViewNodeTrailUseCase viewNodeTrailUseCase;

    public NodeProgressController(
            RecordOutputUseCase recordOutputUseCase,
            BlockNodeUseCase blockNodeUseCase,
            NudgeUseCase nudgeUseCase,
            EnrichNodeUseCase enrichNodeUseCase,
            ViewNodeTrailUseCase viewNodeTrailUseCase) {
        this.recordOutputUseCase = recordOutputUseCase;
        this.blockNodeUseCase = blockNodeUseCase;
        this.nudgeUseCase = nudgeUseCase;
        this.enrichNodeUseCase = enrichNodeUseCase;
        this.viewNodeTrailUseCase = viewNodeTrailUseCase;
    }

    @Operation(
            summary = "What is this work, actually?",
            description =
                    """
                    DISCOVERY-ENRICH-NODE-01. Requires WORK_NODE_MARK — the same permission as the five
                    taps beside it, because this is the same act: a person saying what they know about
                    work they can already see.

                    The click captured that work happened; this captures what it was. A node's text is a
                    sentence copied from a message, and a message is not a description of work: measured
                    on 2026-08-31, 150 of 200 nodes carry no work description at all, and 'Fine by me,
                    that gives us room.' is stored as ADS work.

                    Additive, never corrective. Each field is filled only where it is empty; a field
                    already answered keeps its answer, and a null field means 'not answering this one'
                    rather than 'clear it'. A request that repeats a set field succeeds and changes
                    nothing — the response says so with accepted=false, which is a success.

                    work_type is deliberately not enrichable. It is derived at bracket open from the
                    address that produced the bracket, and a second writer would be a second answer.

                    Allowed at any point in a node's life, including after it has closed or lapsed.
                    Enrichment walks no arrow of machine 4.1, and the person reviewing last week's work
                    is exactly the person best placed to name it.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "What the node carries now, and whether anything was taken"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold WORK_NODE_MARK",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "UNKNOWN_WORK_NODE: no such unit of work",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/nodes/{nodeId}/enrichment")
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    public ResponseEntity<NodeEnrichmentResponse> enrich(
            @PathVariable UUID nodeId, @Valid @RequestBody EnrichNodeRequest request) {
        EnrichNodeUseCase.Enriched enriched = enrichNodeUseCase.execute(
                new EnrichNodeUseCase.Enrich(nodeId, request.title(), request.detail(), request.checklist()));

        return ResponseEntity.ok(new NodeEnrichmentResponse(
                enriched.node().value(),
                enriched.title().orElse(null),
                enriched.detail().orElse(null),
                enriched.checklist().orElse(null),
                enriched.accepted(),
                enriched.correctable()));
    }

    @Operation(
            summary = "What did this work actually do?",
            description =
                    """
                    DISCOVERY-VIEW-NODE-TRAIL-01. Requires WORK_NODE_MARK. Every move the node made,
                    oldest first, appended and never updated.

                    work_node.state is a single overwriting column, so a node that went MARKED →
                    ASSIGNED → IN_PROGRESS → BLOCKED → IN_PROGRESS → COMPLETED → CLOSED reads afterwards
                    as CLOSED and nothing else. That lifetime is what S3 Effort measures — how long work
                    took, how much of it was blocked, how much changed hands — and none of it is
                    recoverable from a column that keeps only the last answer.

                    actorId is null where nothing moved it: the nudge sweep lapsing work nobody answered
                    for, a loop reopening a completed node. Work somebody dropped and work nobody came
                    back to are different findings, so the absence is reported rather than filled in.

                    recordedFromTheStart is false for every node marked before V92, which is almost all
                    of them today. An empty trail is an honest answer, not a fault: the trail records
                    moves made after it existed and cannot invent the ones before.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The moves, oldest first"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold WORK_NODE_MARK",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "UNKNOWN_WORK_NODE: no such unit of work",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/nodes/{nodeId}/trail")
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    public ResponseEntity<NodeTrailResponse> trail(@PathVariable UUID nodeId) {
        ViewNodeTrailUseCase.Trail found = viewNodeTrailUseCase.execute(nodeId);
        return ResponseEntity.ok(new NodeTrailResponse(
                found.node().value(),
                found.recordedFromTheStart(),
                found.moves().stream()
                        .map(move -> new NodeTrailResponse.Move(
                                move.from().map(Enum::name).orElse(null),
                                move.to().name(),
                                move.actorId().orElse(null),
                                move.reason().orElse(null),
                                move.occurredAt()))
                        .toList()));
    }

    @Operation(
            summary = "Done — produced what?",
            description =
                    """
                    DISCOVERY-RECORD-OUTPUT-01. Requires WORK_NODE_MARK. The highest-value single tap in
                    the system: the answer is the strongest component of the fingerprint and the primary
                    track-end signal, and neither is recoverable from anything else in the data.

                    It is never inferred. A fabricated output type is worse than a missing one, because it
                    is undetectable downstream and every median built on it is quietly wrong.

                    NONE — 'no output yet' — keeps the work open. The answer is recorded, the message
                    stays as evidence, and the work carries on; the person answers properly when there is
                    something to name. Machine 4.1 draws no Completed -> Completed arrow, so a NONE node
                    that had reached Completed could never record a real output afterwards: it could not
                    close, could not lapse, and would sit in the graph as work that finished having
                    produced nothing.

                    Tapping Done on work nobody explicitly started is the normal case, and two drawn
                    arrows are walked in one call — Assigned or Self to InProgress, then InProgress to
                    Completed. Nothing undrawn is added: Done from Marked and Done from Blocked both
                    refuse, the second because the person has to say the wait ended before the work can.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Where the work stands, and which clock is now running"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold WORK_NODE_MARK",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "ILLEGAL_NODE_TRANSITION: machine 4.1 draws no arrow from where this work is"
                        + " · OUTPUT_ALREADY_RECORDED: the first answer stands",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "UNKNOWN_WORK_NODE: no such unit of work",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/nodes/{nodeId}/output")
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    public ResponseEntity<NodeProgressResponse> recordOutput(
            @PathVariable UUID nodeId, @Valid @RequestBody RecordOutputRequest request) {
        RecordOutputUseCase.Recorded recorded = recordOutputUseCase.execute(
                new RecordOutputUseCase.RecordOutput(nodeId, OutputType.valueOf(request.outputType())));

        String openPhase = recorded.output().closesTheNode() ? "REVIEW" : "WORK";
        return ResponseEntity.ok(new NodeProgressResponse(
                recorded.node().value(),
                recorded.state().name(),
                recorded.output().name(),
                openPhase,
                null,
                recorded.pairedWith().map(WorkNodeId::value).orElse(null),
                false));
    }

    @Operation(
            summary = "Blocked — waiting on whom?",
            description =
                    """
                    DISCOVERY-BLOCK-NODE-01. Requires WORK_NODE_MARK. Four buttons and no free-text field.

                    The client/supplier split is not cosmetic: it is what makes invariant I4 computable.
                    External waiting is somebody else's silence and must never enter a figure about a
                    person, and a free-text reason cannot be aggregated — so a system that collected one
                    would have recorded the fact and lost the ability to use it. CLIENT and SUPPLIER open
                    an EXTERNAL_WAIT segment; COLLEAGUE and APPROVAL open an INTERNAL_WAIT one.

                    Discovery cannot borrow the application's phases here. Those have one BLOCKED state
                    and a free-text reason, which cannot answer 'did the client keep us waiting'.

                    The open segment is sealed and a new one opened. They are separate rows and are never
                    summed — invariant I9 — so there is no total field on the response and none behind it.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The wait that is now running, and whose silence it is"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold WORK_NODE_MARK",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "ILLEGAL_NODE_TRANSITION: only work in progress can be blocked",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "UNKNOWN_WORK_NODE: no such unit of work",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/nodes/{nodeId}/block")
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    public ResponseEntity<NodeProgressResponse> block(
            @PathVariable UUID nodeId, @Valid @RequestBody BlockNodeRequest request) {
        BlockNodeUseCase.Blocked blocked =
                blockNodeUseCase.block(new BlockNodeUseCase.BlockNode(nodeId, WaitingOn.valueOf(request.waitingOn())));

        return ResponseEntity.ok(progress(blocked));
    }

    @Operation(
            summary = "Resume — whoever was being waited on came back",
            description =
                    """
                    DISCOVERY-BLOCK-NODE-01. Requires WORK_NODE_MARK. The wait segment is sealed and a
                    fresh WORK segment opens.

                    A new row rather than a reopened one, and that is the refusal rather than tidiness: a
                    segment that could be re-ended could be lengthened after the fact, and every median
                    built from it would move with nothing recording that it had.

                    It carries no body. There is nothing to ask a person here — the answer that mattered
                    was given when the work stopped.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Work is running again, on a new segment"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold WORK_NODE_MARK",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "ILLEGAL_NODE_TRANSITION: only blocked work resumes",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "UNKNOWN_WORK_NODE: no such unit of work",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/nodes/{nodeId}/resume")
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    public ResponseEntity<NodeProgressResponse> resume(@PathVariable UUID nodeId) {
        return ResponseEntity.ok(progress(blockNodeUseCase.resume(new BlockNodeUseCase.ResumeNode(nodeId))));
    }

    @Operation(
            summary = "The one thing worth asking this person about",
            description =
                    """
                    DISCOVERY-NUDGE-COMPLETION-01. Requires WORK_NODE_MARK. One unit of work, or 204.

                    One, never a list. A queue of these would be the growing number this feature refuses
                    everywhere: a count that goes up is a count people stop opening.

                    204 is the ordinary answer and not a failure. Most of the time there is nothing worth
                    anybody's attention, and a question asked for the sake of asking is what teaches
                    people to stop reading the questions.

                    It offers the person's own oldest unanswered work, because the oldest is the one most
                    likely to have quietly died — and their own, because only the performer can answer
                    'is this still going' without guessing.
                    """)
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "The unit of work to ask about, and the words it was marked from"),
        @ApiResponse(responseCode = "204", description = "Nothing worth asking about", content = @Content),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold WORK_NODE_MARK",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/nudge")
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    public ResponseEntity<NudgeResponse> nudge() {
        return nudgeUseCase
                .nextToAskAbout()
                .map(asking -> ResponseEntity.ok(new NudgeResponse(
                        asking.node().value(),
                        asking.job().value(),
                        asking.track().map(TrackId::value).orElse(null),
                        asking.text(),
                        asking.state().name())))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(
            summary = "Still going, or done?",
            description =
                    """
                    DISCOVERY-NUDGE-COMPLETION-01. Requires WORK_NODE_MARK. One nudge per node, for all
                    time — a second would teach people to ignore the first, and this product has exactly
                    one input to protect. It is a rule rather than a setting: making the count
                    configurable would only invite somebody to try two.

                    'It was a question' is the fourth answer and it earns its place. 'Where's the Aurora
                    design?' can be answered but never completed, so without it such nodes drift to Lapsed
                    — which means failed capture — and quietly degrade the coverage figure that gates
                    every discovered type. It is a human recognising their own message and is NEVER
                    inferred from wording (invariant I11): a misread question fabricates a duration.

                    'Done' does not record an output. Saying the work finished does not say what it
                    produced, and this endpoint will not invent that answer; asksForAnOutput is true and
                    the person meets DISCOVERY-RECORD-OUTPUT-01's five buttons next.

                    'Dropped' is answerable from every state a node can be nudged in — Assigned,
                    InProgress, Self and Blocked. DISCOVERY_REQ_SPEC_01 was raised when the last two
                    refused, and was granted on 2026-08-25: two approved specifications disagreed about
                    what a person is offered, and DISCOVERY_04 §3 won because it describes what somebody
                    meets. Solo work that dies, and work blocked on a client who never replies, now have
                    somewhere to go.

                    'It was a question' is answerable from Marked, Assigned and Self, and refused from
                    InProgress and Blocked — and that refusal is deliberate rather than an oversight. A
                    query contributes no duration (I11), so converting a node that has already run a
                    clock would discard a measured stretch of work with nothing left to detect the loss.
                    The Query branch is reachable only from states where no clock has started.

                    A refused answer costs the person nothing: the nudge is stamped in the same
                    transaction as the transition, so a refusal rolls both back and the work is offered
                    again.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Where the work stands after the answer"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold WORK_NODE_MARK",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "ALREADY_NUDGED: once is the whole of it"
                        + " · ILLEGAL_NODE_TRANSITION: machine 4.1 draws no arrow from where this work is",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "UNKNOWN_WORK_NODE: no such unit of work",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/nodes/{nodeId}/nudge-answer")
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    public ResponseEntity<NodeProgressResponse> answerNudge(
            @PathVariable UUID nodeId, @Valid @RequestBody NudgeAnswerRequest request) {
        NudgeUseCase.Answered answered =
                nudgeUseCase.answer(new NudgeUseCase.AnswerNudge(nodeId, NudgeAnswer.valueOf(request.answer())));

        return ResponseEntity.ok(new NodeProgressResponse(
                answered.node().value(), answered.state().name(), null, null, null, null, answered.asksForAnOutput()));
    }

    private static NodeProgressResponse progress(BlockNodeUseCase.Blocked blocked) {
        return new NodeProgressResponse(
                blocked.node().value(),
                blocked.openPhase().isWait() ? "BLOCKED" : "IN_PROGRESS",
                null,
                blocked.openPhase().name(),
                blocked.waitingOn() == null ? null : blocked.waitingOn().name(),
                null,
                false);
    }
}
