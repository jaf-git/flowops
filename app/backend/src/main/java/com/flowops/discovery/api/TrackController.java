package com.flowops.discovery.api;

import com.flowops.discovery.api.dto.AssignOrphanRequest;
import com.flowops.discovery.api.dto.EndThreadResponse;
import com.flowops.discovery.api.dto.OrphanRow;
import com.flowops.discovery.application.assignorphan.AssignOrphanUseCase;
import com.flowops.discovery.application.endthread.EndThreadUseCase;
import com.flowops.shared.web.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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
public class TrackController {
    private final EndThreadUseCase endThreadUseCase;
    private final AssignOrphanUseCase assignOrphanUseCase;

    public TrackController(EndThreadUseCase endThreadUseCase, AssignOrphanUseCase assignOrphanUseCase) {
        this.endThreadUseCase = endThreadUseCase;
        this.assignOrphanUseCase = assignOrphanUseCase;
    }

    @Operation(
            summary = "End this thread",
            description =
                    """
                    DISCOVERY-END-THREAD-01. Requires WORK_NODE_MARK, which every role holds — the
                    person who started the work knows when it ended, and asking them once is cheaper and
                    truer than any mechanism guessing.

                    One control, and it settles the weakest joint in this design. Three of the
                    fingerprint's six components — position in the thread, the following role, the median
                    work-phase duration — cannot resolve until a thread closes, so until this endpoint
                    exists the zone accumulates a graph and discovers nothing from it. Every attempt to
                    infer the close failed: a terminal output arrives on threads that carry on, dormancy
                    is silence rather than an ending, and an engagement closing cuts everything in it
                    mid-flight.

                    The response carries a `completeness` nobody sent. It is computed here and FROZEN —
                    invariant I12, and there is no route that recomputes it. COMPLETE where a request met
                    a paired completion carrying a terminal output; START_ONLY where only the opening
                    node exists; PARTIAL otherwise. START_ONLY is excluded from discovery outright,
                    because a thread started and never completed carries whatever duration ran before it
                    went quiet: it does not look incomplete, it looks efficient, and the exclusion is
                    what stops silence being read as speed.

                    A second press is refused rather than swallowed. A thread never reopens and never
                    closes twice: two closure events would make every sequence inferred from closure
                    wrong, silently.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The thread's ending, as it was written"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold WORK_NODE_MARK",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "TRACK_ALREADY_CLOSED: the thread has already ended, and its completeness stands",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description =
                        "UNKNOWN_TRACK: no such thread — threads are inferred from work, never conjured on demand",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/tracks/{trackId}/end")
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    public ResponseEntity<EndThreadResponse> endThread(@PathVariable UUID trackId) {
        return ResponseEntity.ok(
                EndThreadResponse.of(endThreadUseCase.execute(new EndThreadUseCase.EndThread(trackId))));
    }

    @Operation(
            summary = "The work no signal could place",
            description =
                    """
                    DISCOVERY-ASSIGN-ORPHAN-01. Requires WORK_NODE_ASSIGN_SUBJECT — the manager's
                    permission, not the employee's. Which thread a stranded node belongs to is
                    classification rather than recognition, and the signals have already failed at it
                    once; asking the person who marked it to guess again is asking for a false
                    relationship by another route.

                    An orphan is a real state rather than a failure. The node is kept, excluded from
                    every sequence computation until it is placed, and offered here — because losing a
                    signal beats building a false relationship: a wrongly-placed node is undetectable
                    afterwards and a missing one is visibly missing.

                    NO FIGURE HERE IS KEYED TO A PERSON — invariant I5. Who said the sentence, yes: a
                    manager cannot place the work without knowing that. A count, a duration or an average
                    attached to that person, never. I5 is enforced by the absence of the route, not by a
                    permission, because a figure a permission hides has already been computed.

                    Status questions are never in this queue. One has no thread by construction and is
                    therefore not homeless, and offering one would ask somebody to file an answered
                    question as work.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Every unit of work waiting to be placed, oldest first"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold WORK_NODE_ASSIGN_SUBJECT",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/orphans")
    @PreAuthorize("hasAuthority('WORK_NODE_ASSIGN_SUBJECT')")
    public ResponseEntity<List<OrphanRow>> orphans() {
        return ResponseEntity.ok(assignOrphanUseCase.awaitingPlacement().stream()
                .map(OrphanRow::of)
                .toList());
    }

    @Operation(
            summary = "Place a stranded unit of work in a thread",
            description =
                    """
                    DISCOVERY-ASSIGN-ORPHAN-01. Requires WORK_NODE_ASSIGN_SUBJECT, the manager's.

                    Only work belonging to no thread may be adopted. Moving a node that already sits in
                    one would re-thread work that has already contributed an inferred order to the graph,
                    which is a rewrite of history rather than a correction of it — so a node already
                    placed is refused rather than moved.

                    A thread in another engagement is refused as an unknown thread. A thread belongs to
                    exactly one job, so placing a node across that boundary would leave its job pointing
                    one way and its thread the other — invariant I2 broken in the one place a person
                    could break it by hand. It answers the same way a thread that does not exist does,
                    because a refusal that reveals a thread in an engagement the caller was not looking
                    at is a refusal that enumerates.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Placed. The node now belongs to that thread"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold WORK_NODE_ASSIGN_SUBJECT",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "NODE_IS_NOT_ORPHAN, or TRACK_ALREADY_CLOSED: nothing joins a closed thread",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "UNKNOWN_WORK_NODE, or UNKNOWN_TRACK — which also answers for a thread in another job",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/nodes/{nodeId}/track")
    @PreAuthorize("hasAuthority('WORK_NODE_ASSIGN_SUBJECT')")
    public ResponseEntity<Void> placeOrphan(
            @PathVariable UUID nodeId, @Valid @RequestBody AssignOrphanRequest request) {
        assignOrphanUseCase.place(nodeId, request.trackId());
        return ResponseEntity.ok().build();
    }
}
