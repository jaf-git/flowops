package com.flowops.discovery.api;

import com.flowops.discovery.api.dto.DigestResponse;
import com.flowops.discovery.api.dto.TypeNameRequest;
import com.flowops.discovery.api.dto.TypeRow;
import com.flowops.discovery.application.digest.WeeklyDigestUseCase;
import com.flowops.discovery.application.nametype.NameTypeUseCase;
import com.flowops.discovery.application.proposetype.ProposeTypeUseCase;
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
public class TypeController {
    private final ProposeTypeUseCase proposeTypeUseCase;
    private final NameTypeUseCase nameTypeUseCase;
    private final WeeklyDigestUseCase weeklyDigestUseCase;

    public TypeController(
            ProposeTypeUseCase proposeTypeUseCase,
            NameTypeUseCase nameTypeUseCase,
            WeeklyDigestUseCase weeklyDigestUseCase) {
        this.proposeTypeUseCase = proposeTypeUseCase;
        this.nameTypeUseCase = nameTypeUseCase;
        this.weeklyDigestUseCase = weeklyDigestUseCase;
    }

    @Operation(
            summary = "The shapes this workspace's work keeps taking",
            description =
                    """
                    DISCOVERY-PROPOSE-TYPE-01. Requires DISCOVERY_TYPE_CURATE — the owner's alone.

                    The catalogue is reconciled against every closed thread the workspace holds before it
                    is returned, so what comes back includes this morning's work rather than the state of
                    the last sweep.

                    THE COUNT IS COMPLETED THREADS, NEVER NODES AND NEVER CYCLES. Counter C2: a design
                    that went through three revision rounds is ONE occurrence with a cycle count of
                    three. Getting that backwards makes revision-heavy work look three times as common
                    as it is and inflates every type it belongs to. It also never decays — a count
                    answers how often, a weight answers how current, and conflating them causes two
                    opposite bugs: a decaying count makes history evaporate, a non-decaying weight keeps
                    dead patterns at the top of the queue forever.

                    Five completed threads of a shape produce a proposal and four do not. The number is
                    workspace configuration rather than a constant, because not one of this feature's
                    eleven thresholds was chosen by measurement — a wrong one does not fail, it produces
                    a proposal slightly too early or a pattern that never surfaces, and both look exactly
                    like the system working.

                    Dismissed and superseded types are absent. A dismissed suggestion never returns
                    unchanged (I10), and a queue that showed the owner what they had already dismissed is
                    the clearest possible way of teaching them that dismissing does nothing.

                    NO ROW CARRIES A PERSON. A type is a hand-over between two roles.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Every live type, strongest evidence first"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold DISCOVERY_TYPE_CURATE",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/types")
    @PreAuthorize("hasAuthority('DISCOVERY_TYPE_CURATE')")
    public ResponseEntity<List<TypeRow>> types() {
        return ResponseEntity.ok(
                proposeTypeUseCase.execute().stream().map(TypeRow::of).toList());
    }

    @Operation(
            summary = "Name a discovered shape of work",
            description =
                    """
                    DISCOVERY-NAME-TYPE-01. Requires DISCOVERY_TYPE_CURATE — the owner's alone, because
                    this fixes a word the whole workspace will use.

                    Ten seconds, once, forever. One field and nothing beside it: a description or a
                    category select would turn the single interaction this feature asks of an owner into
                    a form, and a form is what people postpone.

                    A second type under one name is refused. Two types called "content production" is two
                    vocabularies for one process, and it divides every count between two rows nobody can
                    tell apart — so the check is case-insensitive, because Content Production and content
                    production are the same word to everybody except a database. A type that already
                    carries a name is found by its own name, so naming one twice meets the same refusal.

                    A type that is not a live proposal answers as unknown. A candidate has not earned the
                    owner's attention and was never in their queue; a dismissed one they have already
                    answered and I10 says it never returns; a superseded one was merged away. The three
                    answer identically, because a refusal that told them apart would let a caller learn
                    the shape of a catalogue they were not looking at.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Named. The workspace now has one word for it"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold DISCOVERY_TYPE_CURATE",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "TYPE_NAME_TAKEN: a type of work already goes by that name",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "UNKNOWN_TRACK_TYPE: no such type, or a type that is not a live proposal",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/types/{typeId}/name")
    @PreAuthorize("hasAuthority('DISCOVERY_TYPE_CURATE')")
    public ResponseEntity<Void> name(@PathVariable UUID typeId, @Valid @RequestBody TypeNameRequest request) {
        nameTypeUseCase.name(typeId, request.name());
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Dismiss a proposed shape of work",
            description =
                    """
                    DISCOVERY-NAME-TYPE-01, the other answer. Requires DISCOVERY_TYPE_CURATE.

                    A DISMISSED SUGGESTION NEVER RETURNS UNCHANGED — invariant I10. The decision is
                    recorded permanently, and what actually keeps the suggestion away is not the row's
                    status but the threads: they go on pointing at the dismissed type, so the next sweep
                    recognises the shape as one the owner has answered rather than as one it has never
                    seen. Re-proposing it would ask a question they have already answered and teach them
                    that answering it does nothing.

                    Pressing it twice is an ordinary thing for a person to do and gets an ordinary
                    answer. The refusal that matters is against the clusterer asking twice, not against
                    the owner clicking twice.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Dismissed, and it will not be proposed again"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold DISCOVERY_TYPE_CURATE",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "UNKNOWN_TRACK_TYPE: no such type, or one that was never a proposal",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/types/{typeId}/dismiss")
    @PreAuthorize("hasAuthority('DISCOVERY_TYPE_CURATE')")
    public ResponseEntity<Void> dismiss(@PathVariable UUID typeId) {
        nameTypeUseCase.dismiss(typeId);
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "The weekly digest",
            description =
                    """
                    DISCOVERY_04 §6. Requires DISCOVERY_CANVAS_VIEW, which managers hold as well as the
                    owner.

                    AT MOST THREE DECISIONS, EVER, ranked by occurrence count times total work time —
                    value consumed rather than recency or count, so a pattern occurring forty times and
                    eating a third of the week outranks one occurring five times. Everything beyond the
                    third waits SILENTLY. The owner's attention is the scarcest resource this feature
                    spends, and a queue that grows is a queue people stop opening.

                    There is no badge, no unread count, no mark-all-read, and NO FIELD ON THIS RESPONSE
                    THAT WOULD LET A SCREEN RENDER ONE. A total of everything pending would be a growing
                    indicator under a different name.

                    PROVISIONAL IS A LABEL ON A TYPE AND NEVER A ROW HERE. A type at its evidence floor
                    oscillates week to week as threads are corrected, and announcing each crossing would
                    spend the entire budget on a type whose real status never changed. The one exception
                    is a demotion caused by COVERAGE falling below its floor — a role has stopped
                    clicking, which somebody can act on — and it surfaces keyed to the role and never to
                    a person.

                    NO PERSON IDENTIFIER AND NO PERSON NAME APPEARS ANYWHERE IN THIS RESPONSE, and no
                    duration either: work-phase time ranks the rows and stays behind the port, because a
                    duration is never presented without its phase (I9).
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The week, and at most three things to decide"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold DISCOVERY_CANVAS_VIEW",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/digest")
    @PreAuthorize("hasAuthority('DISCOVERY_CANVAS_VIEW')")
    public ResponseEntity<DigestResponse> digest() {
        return ResponseEntity.ok(DigestResponse.of(weeklyDigestUseCase.execute()));
    }
}
