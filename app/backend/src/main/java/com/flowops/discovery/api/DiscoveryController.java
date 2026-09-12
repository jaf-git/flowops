package com.flowops.discovery.api;

import com.flowops.discovery.api.dto.JobGuessResponse;
import com.flowops.discovery.api.dto.MarkMessageRequest;
import com.flowops.discovery.api.dto.MarkedResponse;
import com.flowops.discovery.api.dto.OpenJobRequest;
import com.flowops.discovery.api.dto.SetSubjectRequest;
import com.flowops.discovery.application.activities.ManageActivities;
import com.flowops.discovery.application.clients.ManageClients;
import com.flowops.discovery.application.jobguess.GuessJobUseCase;
import com.flowops.discovery.application.markmessage.MarkMessageUseCase;
import com.flowops.discovery.application.openjob.OpenJobUseCase;
import com.flowops.discovery.application.setsubject.SetSubjectUseCase;
import com.flowops.discovery.application.shared.port.IdentifyCallerPort;
import com.flowops.discovery.application.undomark.UndoMarkUseCase;
import com.flowops.discovery.domain.enums.CounterpartyKind;
import com.flowops.discovery.domain.enums.Direction;
import com.flowops.discovery.domain.model.Activity;
import com.flowops.discovery.domain.model.Counterparty;
import com.flowops.discovery.domain.model.JobId;
import com.flowops.discovery.domain.model.TrackId;
import com.flowops.shared.web.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/discovery")
@Tag(
        name = "Discovery",
        description = "The work a business does, discovered from what its people said rather than described.")
public class DiscoveryController {
    private final OpenJobUseCase openJobUseCase;
    private final MarkMessageUseCase markMessageUseCase;
    private final UndoMarkUseCase undoMarkUseCase;
    private final SetSubjectUseCase setSubjectUseCase;
    private final GuessJobUseCase guessJobUseCase;
    private final ManageClients clients;
    private final ManageActivities activities;
    private final IdentifyCallerPort caller;

    public DiscoveryController(
            OpenJobUseCase openJobUseCase,
            MarkMessageUseCase markMessageUseCase,
            UndoMarkUseCase undoMarkUseCase,
            SetSubjectUseCase setSubjectUseCase,
            GuessJobUseCase guessJobUseCase,
            ManageClients clients,
            ManageActivities activities,
            IdentifyCallerPort caller) {
        this.openJobUseCase = openJobUseCase;
        this.markMessageUseCase = markMessageUseCase;
        this.undoMarkUseCase = undoMarkUseCase;
        this.setSubjectUseCase = setSubjectUseCase;
        this.guessJobUseCase = guessJobUseCase;
        this.clients = clients;
        this.activities = activities;
        this.caller = caller;
    }

    @Operation(
            summary = "Open an engagement from a message",
            description =
                    """
                    DISCOVERY-OPEN-JOB-01. Requires WORK_NODE_MARK, which every employee holds — this
                    zone has nothing to analyse unless the people doing the work click, so a permission
                    gate here would be a gate on its only supply of evidence. It is bounded by what the
                    caller can already see: marking requires participating in the conversation, which
                    CHAT enforces, so this widens nothing.

                    This is the one piece of structure nobody can infer. Recovering which instance an
                    event belongs to is the hardest known problem in process mining and the literature's
                    heuristics for it are unreliable; one tap settles it, from the person who knows.

                    The message becomes the engagement's first unit of work, carrying the start boundary.
                    """)
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "The engagement, the unit of work that opened it, and the thread that work opened"),
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
    @PostMapping("/jobs")
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    public ResponseEntity<MarkedResponse> openJob(@Valid @RequestBody OpenJobRequest request) {
        OpenJobUseCase.Opened opened = openJobUseCase.execute(new OpenJobUseCase.OpenJob(
                request.messageId(),
                request.name(),
                request.projectLabel(),
                request.counterpartyId(),
                request.reworkOfJobId(),
                request.evenThoughOneIsOpen()));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new MarkedResponse(
                        opened.node().value(),
                        opened.job().value(),
                        opened.track().map(TrackId::value).orElse(null),
                        false));
    }

    @Operation(
            summary = "Mark a message as work",
            description =
                    """
                    DISCOVERY-MARK-MESSAGE-01. Requires WORK_NODE_MARK. The circle, and the only input
                    this zone has.

                    The direction is chosen by the person and never read out of the words: classifying by
                    content is refused, and a wrong direction fabricates a duration by pairing a
                    completion with a request that was never its own.

                    The thread is inferred and costs no clicks. A null trackId in the response is a real
                    answer twice over — a status question never joins a thread, and a node no signal
                    could place is an orphan, kept and surfaced for a manager rather than guessed at.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "The unit of work, and the thread it landed in"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold WORK_NODE_MARK",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "MESSAGE_NOT_MARKABLE or UNKNOWN_JOB",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/nodes")
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    public ResponseEntity<MarkedResponse> mark(@Valid @RequestBody MarkMessageRequest request) {
        MarkMessageUseCase.Marked marked = markMessageUseCase.execute(new MarkMessageUseCase.MarkMessage(
                request.messageId(), request.jobId(), Direction.valueOf(request.direction()), request.performerId()));

        UUID track = marked.track().map(TrackId::value).orElse(null);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new MarkedResponse(marked.node().value(), request.jobId(), track, false));
    }

    @Operation(
            summary = "Take a mark back, inside ten seconds",
            description =
                    """
                    DISCOVERY-MARK-MESSAGE-01. Requires WORK_NODE_MARK — the permission to mark a message
                    is the permission to unmark it, because a click somebody cannot withdraw is a click
                    they hesitate over, and this zone has nothing to analyse unless the people doing the
                    work click.

                    A full delete rather than a tombstone. Somebody pressed the circle by accident and
                    withdrew it before anything else happened; there is no fact there to preserve, and a
                    tombstone would leave a row every later query has to remember to filter. The thread
                    goes too if the withdrawn work was its only member, and the engagement goes if this
                    was the click that opened it.

                    The ten seconds are measured here, against the row's own creation instant. The
                    countdown drawn in the toast is presentation: a refusal that trusted it would let a
                    tab left open overnight delete work that has since been threaded and counted.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Taken back, evidence and all"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold WORK_NODE_MARK",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "UNDO_WINDOW_CLOSED: the ten seconds have run out and the work stays",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "UNKNOWN_WORK_NODE: there is no such unit of work to take back",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/nodes/{nodeId}")
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    public ResponseEntity<Void> undo(@PathVariable UUID nodeId) {
        undoMarkUseCase.execute(nodeId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Answer the subject chip",
            description =
                    """
                    DISCOVERY-SET-SUBJECT-01. Requires WORK_NODE_MARK, which every employee holds:
                    *which engagement is this?* is recognition rather than classification, and it is
                    asked of the person who just did the thing because they know it without thinking.
                    DISCOVERY_02 §3 is the rule the permission table falls out of.

                    Naming the engagement the work already carries records CONFIRMED and moves nothing —
                    *the guess was right* is evidence, and silence is not. Naming a different one records
                    CORRECTED and re-runs the thread inference, because a thread is the inner case
                    identifier and never spans two engagements: work left in its old thread would sit
                    among work it has nothing to do with, and a wrongly-placed unit of work is
                    undetectable afterwards while a missing one is visibly missing. A thread whose last
                    member leaves is deleted rather than kept empty.

                    A null trackId in the response is a real answer: a status question never joins a
                    thread, and a node no signal could place in the new engagement is an orphan, kept and
                    surfaced for a manager rather than guessed at.
                    """)
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "The unit of work, its engagement, and the thread it is in now"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold WORK_NODE_MARK",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "UNKNOWN_JOB: no such engagement, refused rather than created silently."
                        + " UNKNOWN_WORK_NODE: no such unit of work",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/nodes/{nodeId}/job")
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    public ResponseEntity<MarkedResponse> setSubject(
            @PathVariable UUID nodeId, @Valid @RequestBody SetSubjectRequest request) {
        SetSubjectUseCase.Corrected corrected =
                setSubjectUseCase.execute(new SetSubjectUseCase.SetSubject(nodeId, request.jobId()));

        return ResponseEntity.ok(new MarkedResponse(
                corrected.node().value(),
                corrected.job().value(),
                corrected.track().map(TrackId::value).orElse(null),
                corrected.weaklyKeyed()));
    }

    @Operation(
            summary = "What the subject chip should offer, and which one it is guessing",
            description =
                    """
                    DISCOVERY-MARK-MESSAGE-01. Requires WORK_NODE_MARK, for the same reason marking does:
                    the chip is part of the click, and a person who cannot read the offer cannot answer
                    it in one tap.

                    The chip is a guess and is shown as one. At most one entry comes back with
                    guessed = true: the engagement this conversation has most recently produced work in,
                    failing that the workspace's most recently touched open engagement, and failing that
                    nothing — no entry carries the flag and the product does not pretend to know.

                    Nothing here is applied. The flag stays a flag and no unit of work is written, because
                    silently linking work to an engagement the person did not see builds a relationship
                    nobody can detect afterwards — losing a signal beats building a false one.
                    """)
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "The engagements to offer, most relevant first, with at most one guessed"),
        @ApiResponse(responseCode = "401", description = "No session", content = @Content),
        @ApiResponse(
                responseCode = "403",
                description = "NOT_PERMITTED: the caller does not hold WORK_NODE_MARK",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/jobs")
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    public ResponseEntity<List<JobGuessResponse>> jobsToOffer(
            @RequestParam(required = false) UUID conversationId,
            @RequestParam(defaultValue = "false") boolean describing) {
        List<GuessJobUseCase.Offer> offers =
                describing ? guessJobUseCase.describing(conversationId) : guessJobUseCase.execute(conversationId);

        return ResponseEntity.ok(offers.stream().map(JobGuessResponse::of).toList());
    }

    @Operation(
            summary = "The clients this business works for",
            description =
                    """
                    DISCOVERY-MANAGE-CLIENTS-01. The list a person picks from when opening an engagement.

                    A picker rather than a text field, and that is the point. Clients are referenced by
                    identifier, so "Aurora Coffee" chosen twice is one account — where a free-text field
                    would let two spellings become two accounts that divide one client's history between
                    them, which is the failure the free-typed project label still has.
                    """)
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Every client, by name")})
    @GetMapping("/clients")
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    public ResponseEntity<List<ClientResponse>> clients() {
        return ResponseEntity.ok(clients.all().stream().map(ClientResponse::of).toList());
    }

    @Operation(
            summary = "Add a client",
            description =
                    """
                    Anyone may add one and it is never blocked — PRINCIPLE-NEVER-BLOCK. It arrives
                    UNCLASSIFIED, which is a working state rather than a gap: the name is real, work can be
                    recorded against it immediately, and a manager says what kind it is when they next look.

                    A name that already exists returns the existing account rather than refusing. Two people
                    adding the same client on one morning are describing one client, and a refusal would
                    send the second of them to invent "Aurora Coffee Ltd" — the split this endpoint exists
                    to prevent, arrived at through the error message.
                    """)
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Added, or the one that already existed")})
    @PostMapping("/clients")
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    public ResponseEntity<ClientResponse> addClient(@Valid @RequestBody ClientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ClientResponse.of(clients.add(request.name())));
    }

    @Operation(
            summary = "Rename a client",
            description =
                    """
                    A typo, a rebrand, a trading name replacing a legal one.

                    Reaches backwards by construction: engagements hold the identifier, so the history
                    follows the rename rather than dividing — which is exactly what typing the new name into
                    the next engagement would have done.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Renamed"),
        @ApiResponse(responseCode = "409", description = "Another client goes by that name", content = @Content),
        @ApiResponse(responseCode = "422", description = "No such client", content = @Content)
    })
    @PutMapping("/clients/{clientId}")
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    public ResponseEntity<Void> renameClient(@PathVariable UUID clientId, @Valid @RequestBody ClientRequest request) {
        clients.rename(clientId, request.name());
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Say what kind of counterparty this is",
            description =
                    """
                    A manager's decision, and it changes behaviour rather than decorating a name: waiting on
                    a CLIENT or a SUPPLIER is external waiting and never enters a figure about how fast this
                    workspace is, while waiting on INTERNAL work is the business's own delay and does.

                    A business whose work sat five days waiting on a client has not been slow, and this is
                    the field that keeps a screen from saying otherwise.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Classified"),
        @ApiResponse(responseCode = "422", description = "No such client", content = @Content)
    })
    @PostMapping("/clients/{clientId}/kind")
    @PreAuthorize("hasAuthority('WORK_NODE_ASSIGN_SUBJECT')")
    public ResponseEntity<Void> classifyClient(
            @PathVariable UUID clientId, @Valid @RequestBody ClientKindRequest request) {
        clients.classify(clientId, request.kind(), caller.currentCaller().orElseThrow());
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Remove a client no engagement names",
            description =
                    """
                    Refused while any engagement points at it, with the count.

                    The alternative is an engagement whose client silently becomes null — not a tidy-up but
                    a return to the state this slice was written to end, and it would happen to the accounts
                    with the most history first.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Removed"),
        @ApiResponse(responseCode = "409", description = "Engagements still name it", content = @Content),
        @ApiResponse(responseCode = "422", description = "No such client", content = @Content)
    })
    @DeleteMapping("/clients/{clientId}")
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    public ResponseEntity<Void> removeClient(@PathVariable UUID clientId) {
        clients.remove(clientId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Say who an engagement is for",
            description =
                    """
                    The ordinary case rather than an edge one: an employee opens an engagement in one click
                    and a manager says who it is for afterwards.

                    Brackets already opened keep the address they were opened with — an address is frozen at
                    open (D1) — so this decides what NEW brackets key on and re-keys nothing already
                    written. That is the same rule the project label follows.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Named"),
        @ApiResponse(responseCode = "422", description = "No such client or engagement", content = @Content)
    })
    @PutMapping("/jobs/{jobId}/client")
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    public ResponseEntity<Void> engagementIsFor(@PathVariable UUID jobId, @Valid @RequestBody ClientChoice request) {
        clients.engagementIsFor(JobId.of(jobId), request.counterpartyId());
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "The activities this workspace names its work by",
            description =
                    """
                    DISCOVERY-MANAGE-ACTIVITIES-01. The list the mark panel filters in the browser, ordered by
                    how often each has been used, so the top of it covers most marks.

                    Fetched once rather than searched. A workspace holds tens of activities, not thousands,
                    and a debounced endpoint behind a text field would spend a network round trip answering
                    what filtering forty rows answers in under a millisecond.

                    Each row carries the departments that have used it. Three or more is surfaced as
                    tooGenericToBeOneThing — "review" means something different in Design and in Legal, and an
                    activity broad enough to be picked everywhere has stopped naming one thing.
                    """)
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Every activity anyone can still pick")})
    @GetMapping("/activities")
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    public ResponseEntity<List<ActivityResponse>> activities() {
        return ResponseEntity.ok(
                activities.catalogue().stream().map(ActivityResponse::of).toList());
    }

    @Operation(
            summary = "Name an activity",
            description =
                    """
                    Anyone marking work may name one and it is never blocked — PRINCIPLE-NEVER-BLOCK.

                    A name that normalises to one already here returns that one rather than refusing, on the
                    same reasoning as adding a client: "Write the caption" and "write the caption " are one
                    activity, and a refusal would send the second person to invent a third name for it.

                    Normalisation folds case and diacritics and reduces everything else to hyphens, because
                    the result is carried inside a step key where a colon, a slash, a hash or a plus each
                    forges a different part of that key's grammar.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Named, or the one that already existed"),
        @ApiResponse(
                responseCode = "400",
                description = "A name that leaves nothing behind once normalised",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/activities")
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    public ResponseEntity<ActivityResponse> nameActivity(@Valid @RequestBody ActivityRequest request) {
        Activity named = activities.add(request.name(), caller.currentCaller().orElseThrow());
        return ResponseEntity.status(HttpStatus.CREATED).body(ActivityResponse.of(named, List.of(), List.of(), false));
    }

    @Operation(
            summary = "Merge one activity into another",
            description =
                    """
                    DISCOVERY-MANAGE-ACTIVITIES-01, step 9. Two names for one piece of work become one, and
                    every node that carried the losing name now carries the surviving one — so the step key
                    they produce collapses to a single step rather than two half-populated ones.

                    A person does this, never the software and never a model. A wrong automatic merge makes
                    two different activities one and the loss is silent and permanent; a missed merge costs
                    one duplicate, which this endpoint fixes whenever somebody notices.

                    The losing activity is kept as MERGED with a pointer to the survivor rather than
                    deleted, because the pointer is the only record of what was decided.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Merged; the surviving activity comes back"),
        @ApiResponse(
                responseCode = "409",
                description = "Into itself, or either side is already merged or retired",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "No such activity",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/activities/{activityId}/merge")
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    public ResponseEntity<ActivityResponse> mergeActivity(
            @PathVariable UUID activityId, @Valid @RequestBody ActivityMergeRequest request) {
        Activity surviving = activities.merge(activityId, request.intoId());
        return ResponseEntity.ok(ActivityResponse.of(surviving, List.of(), List.of(), false));
    }

    @Operation(
            summary = "Retire an activity",
            description =
                    """
                    DISCOVERY-MANAGE-ACTIVITIES-01, step 9. Nobody can pick it again, and nothing already
                    marked with it changes — a step key that has been discovered stays the step it was, or
                    every shape built on it would silently become a different shape.

                    Retiring is for a name that should stop spreading, not for one that duplicates another.
                    A duplicate is merged, so the work joins up; retiring a duplicate would leave its nodes
                    stranded under a name no one can choose again.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Retired"),
        @ApiResponse(
                responseCode = "409",
                description = "Already merged into another activity",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "422",
                description = "No such activity",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/activities/{activityId}/retire")
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    public ResponseEntity<ActivityResponse> retireActivity(@PathVariable UUID activityId) {
        return ResponseEntity.ok(ActivityResponse.of(activities.retire(activityId), List.of(), List.of(), false));
    }

    public record ActivityRequest(@NotBlank @Size(max = 120) String name) {}

    public record ActivityMergeRequest(@NotNull UUID intoId) {}

    public record ActivityResponse(
            UUID id,
            String name,
            String slug,
            String status,
            int timesUsed,
            String lastUsedAt,
            List<String> departments,
            List<String> counterparties,
            boolean tooGenericToBeOneThing) {
        static ActivityResponse of(ManageActivities.Listed listed) {
            return of(listed.activity(), listed.departments(), listed.clients(), listed.tooGenericToBeOneThing());
        }

        static ActivityResponse of(
                Activity activity, List<String> departments, List<String> counterparties, boolean tooGeneric) {
            return new ActivityResponse(
                    activity.id(),
                    activity.name(),
                    activity.slug(),
                    activity.status().name(),
                    activity.timesUsed(),
                    activity.lastUsedAt().map(Object::toString).orElse(null),
                    departments,
                    counterparties,
                    tooGeneric);
        }
    }

    public record ClientRequest(@NotBlank @Size(max = 200) String name) {}

    public record ClientKindRequest(@NotNull CounterpartyKind kind) {}

    public record ClientChoice(@NotNull UUID counterpartyId) {}

    public record ClientResponse(UUID id, String name, CounterpartyKind kind, boolean classified) {
        static ClientResponse of(Counterparty counterparty) {
            return new ClientResponse(
                    counterparty.id(), counterparty.name(), counterparty.kind(), counterparty.isClassified());
        }
    }
}
