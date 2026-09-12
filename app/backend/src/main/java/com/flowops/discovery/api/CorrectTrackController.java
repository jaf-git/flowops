package com.flowops.discovery.api;

import com.flowops.discovery.api.dto.CorrectTrackRequest;
import com.flowops.discovery.application.correcttrack.CorrectTrackUseCase;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/discovery")
@Tag(
        name = "Discovery",
        description = "The work a business does, discovered from what its people said rather than described.")
public class CorrectTrackController {
    private final CorrectTrackUseCase correctTrackUseCase;

    public CorrectTrackController(CorrectTrackUseCase correctTrackUseCase) {
        this.correctTrackUseCase = correctTrackUseCase;
    }

    @Operation(
            summary = "Move a card into the thread it actually belonged to",
            description =
                    """
                    DISCOVERY-CORRECT-TRACK-01. Requires DISCOVERY_TYPE_CURATE — the owner's alone.

                    THIS IS NOT THE ORPHAN QUEUE, AND THE PERMISSION IS THE DIFFERENCE. Placing an
                    orphan (PATCH /nodes/{id}/track, WORK_NODE_ASSIGN_SUBJECT) is a manager answering
                    which thread a piece of stranded work belongs to — the signals failed, and nothing
                    downstream has concluded anything from it yet. Moving a card that is already placed
                    is an owner changing what the product has already concluded.

                    THE RECOMPUTE IS NOT OPTIONAL. A card leaving a closed thread changes that thread's
                    shape, and a thread's shape is what a discovered type is counted from — so this can
                    take the fifth piece of evidence out from under a type somebody has already named.
                    The catalogue is recounted inside the same transaction, and a type that no longer
                    clears its floor becomes PROVISIONAL rather than staying NAMED (I13). The name and
                    the owner's confirmation survive, so when the evidence comes back the type recovers
                    without anybody being asked to name it twice.

                    THE DEMOTION IS SILENT. A type sitting at its floor oscillates week to week, and
                    announcing each crossing would spend the owner's entire three-decision budget on a
                    type whose real status never changed. PROVISIONAL is a label on the type, not an
                    event in anybody's inbox.

                    A CARD NEVER MOVES BETWEEN ENGAGEMENTS (I1, I2). A thread belongs to exactly one
                    job, and a thread in another one is refused as UNKNOWN_TRACK rather than as a
                    distinguishable answer — a refusal that told the caller a thread exists in an
                    engagement they were not looking at is a refusal that enumerates.

                    The lane a card is dropped INTO must be open; the lane it leaves need not be. The
                    closing instant of a thread is what every duration inside it was measured against,
                    so nothing joins a closed thread — but only closed threads are evidence, so if a
                    card could never leave one, no correction could ever change a count and I13 would
                    describe a situation that cannot arise.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Moved, and the catalogue recounted"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold DISCOVERY_TYPE_CURATE. A manager"
                        + " holding WORK_NODE_ASSIGN_SUBJECT meets this, and that is the point",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "TRACK_ALREADY_CLOSED: nothing joins a closed thread",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "UNKNOWN_WORK_NODE: no such unit of work; or UNKNOWN_TRACK: no such thread,"
                        + " or one belonging to another engagement",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/nodes/{nodeId}/lane")
    @PreAuthorize("hasAuthority('DISCOVERY_TYPE_CURATE')")
    public ResponseEntity<Void> moveToLane(@PathVariable UUID nodeId, @Valid @RequestBody CorrectTrackRequest request) {
        correctTrackUseCase.moveToLane(nodeId, request.trackId());
        return ResponseEntity.ok().build();
    }
}
