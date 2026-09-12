package com.flowops.discovery.api;

import com.flowops.discovery.application.deliverwork.DeliverWork;
import com.flowops.discovery.application.markwork.MarkWorkUseCase;
import com.flowops.discovery.domain.enums.MarkVerb;
import com.flowops.shared.web.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/discovery/work")
@Tag(
        name = "Discovery · the two circles",
        description = "One press marks a message as work and places it; one press delivers it.")
public class WorkController {
    private final MarkWorkUseCase marking;
    private final DeliverWork delivering;

    public WorkController(MarkWorkUseCase marking, DeliverWork delivering) {
        this.marking = marking;
        this.delivering = delivering;
    }

    @Operation(
            summary = "Mark a message as work — one press, node and placement together",
            description =
                    """
                    DISCOVERY-MARK-WORK-01. Requires WORK_NODE_MARK, which every employee holds: this zone
                    has nothing to analyse unless the people doing the work click, so a stricter gate here
                    would be a gate on its own supply of evidence. Bounded by participation in the
                    conversation, which CHAT enforces.

                    **The node, its evidence and its placement are one transaction.** The two-step flow this
                    replaces had a state between them that no screen showed, and work left in it was in no
                    bracket, no address and no count with nothing saying so. Merging the steps does not make
                    the second more reliable; it removes the state in which the loss was possible.

                    The verb says what the person meant. CREATE opens new work and is refused where a
                    bracket is already open at that address — R1.1 is a partial unique index, so joining
                    quietly instead would land somebody's tap inside work they did not mean to touch. ADD
                    joins work already open there, which is the one-tap case and the common one. JOIN opens
                    the joiner's OWN bracket beside somebody else's and records that the two belong
                    together: a bracket never has two performers, and the declared join is the one
                    collaboration signal that cannot be derived afterwards.

                    Nothing reads the words. The direction is derived from the button — naming somebody
                    else is a request, marking under your own name is not — and the cascade that used to
                    ask is deleted.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Marked and placed. Says whether it joined."),
        @ApiResponse(
                responseCode = "400",
                description = "REFUSED: the join names work in another engagement, or the container itself",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold WORK_NODE_MARK",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "THAT_WORK_IS_ALREADY_OPEN, or the engagement has ended",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "MESSAGE_NOT_MARKABLE or UNKNOWN_JOB",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    public ResponseEntity<MarkWorkResponse> mark(@Valid @RequestBody MarkWorkRequest request) {
        MarkWorkUseCase.Placed placed = marking.execute(new MarkWorkUseCase.MarkWork(
                request.messageId(),
                request.jobId(),
                request.performerId(),
                request.workType(),
                request.activityId(),
                request.verb(),
                request.joining()));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new MarkWorkResponse(
                        placed.node().value(),
                        placed.bracket().value(),
                        placed.joined(),
                        placed.destination(),
                        placed.workType(),
                        placed.activity(),
                        placed.joinedWith()));
    }

    public record MarkWorkRequest(
            @NotNull UUID messageId,
            @NotNull UUID jobId,
            UUID performerId,
            String workType,
            UUID activityId,
            @NotNull MarkVerb verb,
            UUID joining) {}

    public record MarkWorkResponse(
            UUID nodeId,
            UUID bracketId,
            boolean joined,
            String destination,
            String workType,
            String activity,
            UUID joinedWith) {}

    @Operation(
            summary = "What the red circle may offer on this message",
            description =
                    """
                    R21.2, and the reason it is a separate read: **the closure right is read before anything
                    is offered, never checked after.** A circle that resolved a bracket, asked "deliver?",
                    and then answered "that is not yours to close" would be refusing a choice somebody had
                    already made, with no way for them to have known beforehand.

                    Returns only work this caller may close, plus who holds the rest — so the strip can say
                    "Tariq holds the photos" instead of offering a tap that would fail. Each row carries who
                    it would unblock, which is what lets the confirmation read "this unblocks Karim".

                    **The engagement's boundary is never among them**, even for the person who holds its
                    closure right (R4a.1). Delivering it would mean closing an engagement from a message.

                    Writes nothing.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "What may be delivered here, and who holds the rest."),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold WORK_NODE_MARK",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "MESSAGE_NOT_MARKABLE: no such message, or not one this caller may see",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{messageId}/deliverable")
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    public ResponseEntity<DeliverWork.Offer> deliverable(@PathVariable UUID messageId) {
        return ResponseEntity.ok(delivering.offerIn(messageId));
    }

    @Operation(
            summary = "Deliver it — this message is the output",
            description =
                    """
                    R21.2. The resolved bracket closes DELIVERED with output_kind MESSAGE_REF and this
                    message as the value: in a chat the evidence and the deliverable are usually the same
                    thing, and R11 stores a reference rather than a file.

                    **Done and Dropped are not here.** Neither has an artifact to point at, both live on the
                    bracket in the Work tab, and their absence is what makes this control safe to press — it
                    can only ever deliver, so it cannot accidentally kill a wait.

                    Resolved by address (R4.5) and then by who holds the closure right. One piece of work is
                    one tap. Several are refused with the candidates named, because closing the wrong one
                    releases the wrong waiter and publishes the wrong output to a client's list, and nothing
                    about that throws. Nothing of yours open is refused naming who holds what is.

                    Closing the last live piece of work is what makes an engagement ready to close, and that
                    is recomputed here rather than by a sweep that would discover it minutes later.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Delivered, with who this unblocked."),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold WORK_NODE_MARK",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "NOTHING_OF_YOURS_IS_OPEN_HERE or SEVERAL_COULD_BE_DELIVERED",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "MESSAGE_NOT_MARKABLE: no such message, or not one this caller may see",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{messageId}/deliver")
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    public ResponseEntity<DeliverWork.Delivered> deliver(
            @PathVariable UUID messageId, @RequestBody(required = false) DeliverRequest request) {
        return ResponseEntity.ok(delivering.deliver(messageId, request == null ? null : request.bracketId()));
    }

    public record DeliverRequest(UUID bracketId) {}
}
