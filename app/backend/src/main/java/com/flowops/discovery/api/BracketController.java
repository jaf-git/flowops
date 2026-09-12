package com.flowops.discovery.api;

import com.flowops.discovery.application.claimbracket.ClaimBracket;
import com.flowops.discovery.application.closebracket.CloseBracket;
import com.flowops.discovery.application.closejob.CloseJob;
import com.flowops.discovery.application.declarewait.DeclareWait;
import com.flowops.discovery.application.handover.HandOverBracket;
import com.flowops.discovery.application.markintobracket.ComposeAddress;
import com.flowops.discovery.application.markintobracket.PlaceMarkInBracket;
import com.flowops.discovery.application.markwork.OfferTheVerbs;
import com.flowops.discovery.application.shared.port.ConversationShelfPort;
import com.flowops.discovery.application.shared.port.ConversationWorkPort;
import com.flowops.discovery.application.shared.port.IdentifyCallerPort;
import com.flowops.discovery.application.shared.port.MarkedMessagePort;
import com.flowops.discovery.application.shared.port.WorkGraphPort;
import com.flowops.discovery.domain.enums.NodeKind;
import com.flowops.discovery.domain.enums.OutputKind;
import com.flowops.discovery.domain.enums.WaitKind;
import com.flowops.discovery.domain.model.BracketId;
import com.flowops.discovery.domain.model.JobId;
import com.flowops.discovery.domain.model.WorkNode;
import com.flowops.discovery.domain.model.WorkNodeId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/discovery/brackets")
@Tag(
        name = "Discovery · brackets",
        description = "Work that a conversation produced: what was marked, what it is waiting on, and how it ended.")
public class BracketController {
    private final ConversationWorkPort conversationWork;
    private final CloseBracket closing;
    private final CloseJob readiness;
    private final HandOverBracket handovers;
    private final ClaimBracket claiming;
    private final DeclareWait waiting;
    private final IdentifyCallerPort caller;
    private final ComposeAddress addresses;
    private final PlaceMarkInBracket marking;
    private final OfferTheVerbs verbs;
    private final JdbcTemplate jdbc;
    private final ConversationShelfPort shelf;
    private final MarkedMessagePort marks;
    private final WorkGraphPort graph;
    private final java.time.Clock clock;

    public BracketController(
            ConversationWorkPort conversationWork,
            CloseBracket closing,
            CloseJob readiness,
            HandOverBracket handovers,
            ClaimBracket claiming,
            DeclareWait waiting,
            IdentifyCallerPort caller,
            ComposeAddress addresses,
            PlaceMarkInBracket marking,
            OfferTheVerbs verbs,
            JdbcTemplate jdbc,
            ConversationShelfPort shelf,
            MarkedMessagePort marks,
            WorkGraphPort graph,
            java.time.Clock clock) {
        this.conversationWork = conversationWork;
        this.closing = closing;
        this.readiness = readiness;
        this.handovers = handovers;
        this.claiming = claiming;
        this.waiting = waiting;
        this.caller = caller;
        this.addresses = addresses;
        this.marking = marking;
        this.verbs = verbs;
        this.jdbc = jdbc;
        this.shelf = shelf;
        this.marks = marks;
        this.graph = graph;
        this.clock = clock;
    }

    @Operation(
            summary = "Where this mark would land, without writing anything",
            description =
                    """
                    R1.3. The strip states the destination before the person commits — "adding to · Summer
                    menu › photos" — with a one-tap change. Nobody is asked to understand brackets; they
                    are shown the answer.

                    The two outcomes read differently on purpose. Adding to is reassurance: the work
                    already exists and this joins it, which is what happens almost every time. Starting is
                    a small warning, because a new bracket is an obligation somebody will have to
                    discharge — and somebody who sees "starting" when they expected "adding to" has been
                    given the one chance to notice before the graph fragments.

                    **It also says which verbs are legal right now** (R21.1), and offering only those is
                    the rule rather than a nicety. CREATE at an address where a bracket is already open
                    collides with R1.1's partial unique index, so a strip that offered it would be offering
                    a tap whose only outcome is an error — and the person would have no way to know
                    beforehand. That is why the verbs live in the strip and not on the message.

                    Exactly one of ADD and CREATE comes back, decided by the same lookup the write runs.
                    JOIN comes back alongside when somebody else has live work in this room, with whose it
                    is — ranked by event order in this room and never by date, which was measured wrong in
                    two of six cases where six people touched several pieces on the same days.

                    Writes nothing. Safe to call on every keystroke of a performer picker.
                    """)
    @ApiResponses({@ApiResponse(responseCode = "200", description = "The destination, and what may be pressed.")})
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    @GetMapping("/preview")
    public ResponseEntity<OfferTheVerbs.Offer> preview(
            @RequestParam UUID jobId,
            @RequestParam UUID conversationId,
            @RequestParam(required = false) UUID performerId,
            @RequestParam(required = false) String workType) {
        return ResponseEntity.ok(verbs.offerFor(JobId.of(jobId), conversationId, performerId, workType));
    }

    @Operation(
            summary = "The work one conversation produced",
            description =
                    """
                    The chat page's Work tab. Every bracket this conversation has produced, live work
                    first, each carrying the messages that became it — which is what makes the tab
                    navigable rather than merely informative.

                    The job's boundary bracket is not among them (R4a.1). It is the engagement's
                    container rather than a piece of work, it is open for the whole life of the job, and
                    it is never joinable — so a client counting this list would report one more live
                    bracket than exists, on every engagement, for ever. The boundary is still returned by
                    the marks read, where it belongs: that is what lets the strip on the opening message
                    say "Opens the engagement".

                    Requires WORK_NODE_MARK, which every employee holds: this zone has nothing to show
                    unless the people doing the work click, so a stricter gate here would be a gate on its
                    own supply of evidence. It is bounded by what the caller can already see, because
                    marking requires participating in the conversation and CHAT enforces that.

                    No figure here is keyed to a person. Rows name who is doing the work; nothing counts,
                    ranks or rates them, and no route exists that could.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The conversation's brackets, live work first."),
        @ApiResponse(responseCode = "403", description = "The caller does not hold WORK_NODE_MARK.")
    })
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    @GetMapping("/conversation/{conversationId}")
    public ResponseEntity<List<ConversationWorkPort.ConversationBracket>> workIn(@PathVariable UUID conversationId) {
        return ResponseEntity.ok(conversationWork.workIn(conversationId));
    }

    @Operation(
            summary = "What this conversation's work is waiting on",
            description =
                    """
                    The chat page's Waiting tab. Open waits only — a satisfied one is history and a
                    cancelled one is a thing that died, and neither is something anybody is still waiting
                    for.

                    Each carries the person's own words rather than a category somebody picked for them,
                    and says whether it is external. CLIENT and SUPPLIER waits never enter a figure about
                    how fast this workspace is: a business whose work sat five days waiting on a client
                    has not been slow, and a screen that failed to mark the difference would invite
                    somebody to read a client's delay as a colleague's.
                    """)
    @ApiResponses({@ApiResponse(responseCode = "200", description = "The open waits, soonest expected first.")})
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    @GetMapping("/conversation/{conversationId}/waits")
    public ResponseEntity<List<ConversationWorkPort.ConversationWait>> waitsIn(@PathVariable UUID conversationId) {
        return ResponseEntity.ok(conversationWork.waitsIn(conversationId));
    }

    @Operation(
            summary = "Which messages in this conversation are already work",
            description =
                    """
                    One call for the whole thread, consulted by the circle that renders beside every line.

                    Without it a mark left no trace. The strip confirmed it in component state, so scrolling
                    away or reopening the thread showed nothing, and no API anywhere could say a sentence had
                    already been marked — the evidence table held the join and no read model consulted it.
                    People marked the same sentence twice because there was nothing to tell them not to.

                    A row carries where the mark went, never what was said: the caller already has the
                    message. A message with a node and no bracket yet comes back with a null bracket and
                    state UNPLACED, which is a real state while marking is two steps and the one most worth
                    showing — work recognised and not yet placed.

                    Requires WORK_NODE_MARK and is bounded by the conversation, which CHAT already scopes to
                    its participants.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Every message here that became work."),
        @ApiResponse(responseCode = "403", description = "The caller does not hold WORK_NODE_MARK.")
    })
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    @GetMapping("/conversation/{conversationId}/marks")
    public ResponseEntity<List<MarkedMessagePort.Mark>> marksIn(@PathVariable UUID conversationId) {
        return ResponseEntity.ok(marks.marksIn(conversationId));
    }

    @Operation(
            summary = "End a bracket",
            description =
                    """
                    The three ends. Delivered produced something others will use and requires its output —
                    refused in the domain, not only here. Done finished and nothing builds on it. Dropped
                    ended without completing and requires a reason.

                    Only the closure holder may do this, and exactly one person holds that right at any
                    moment: it defaults to whoever marked the message, transfers to a named performer, and
                    walks up the management line when somebody leaves. No bracket may ever become
                    unclosable.

                    Delivered and Done release everybody waiting on this work. Dropped does not — it warns
                    them instead, because nothing arrived, and releasing them would measure every duration
                    downstream from a delivery that never happened.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ended, with what it released and what it warned."),
        @ApiResponse(responseCode = "400", description = "Delivered with no output, or dropped with no reason."),
        @ApiResponse(responseCode = "409", description = "The caller does not hold the closure right.")
    })
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    @PostMapping("/{bracketId}/close")
    public ResponseEntity<CloseResponse> close(@PathVariable UUID bracketId, @RequestBody CloseRequest request) {
        UUID me = caller.currentCaller().orElseThrow();
        BracketId id = BracketId.of(bracketId);

        var outcome =
                switch (request.kind()) {
                    case DELIVERED -> closing.delivered(
                            id, outputKind(request.outputKind()), request.outputValue(), me);
                    case DONE -> closing.done(id, me);
                    case DROPPED -> closing.dropped(id, request.reason(), me);
                };

        readiness.reconsider(outcome.job());

        return ResponseEntity.ok(new CloseResponse(
                outcome.bracket().value(),
                request.kind().name(),
                outcome.released().size(),
                outcome.bereaved().size(),
                outcome.childrenForceClosed().size()));
    }

    @Operation(
            summary = "Take on work that was going spare",
            description =
                    """
                    Work gets posted in a group chat that plainly needs doing before anybody has said
                    they will do it. Marking that names no performer, so it opens an unclaimed bracket —
                    an address with a hole in it — and somebody picks it up later.

                    Claiming sets the performer AND moves the closure right to the claimer. Taking work
                    on is the opt-in that carries the obligation to finish it. Leaving it with whoever
                    noticed the work would mean the person doing it cannot close it, and the person who
                    can has no idea when it is done.

                    If the claimer already has an open bracket at the resulting address, the two are one
                    piece of work described twice and they MERGE: the unclaimed one's evidence moves,
                    it closes MERGED, and anybody waiting on it is re-targeted rather than told their
                    work died — because it did not die, it is being done three feet away. The response
                    says which happened, so the page can say "added to the photos you already had open"
                    rather than silently moving somebody's tap to a bracket they did not press.

                    Only unclaimed work can be claimed. Taking somebody else's work over is a handover,
                    which keeps both facts; re-pointing a claimed bracket would merge two people's
                    durations into one thread with no record they had ever been two.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Claimed, or merged into work they already had open."),
        @ApiResponse(responseCode = "409", description = "That work already belongs to somebody."),
        @ApiResponse(responseCode = "403", description = "The caller does not hold WORK_NODE_MARK.")
    })
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    @PostMapping("/{bracketId}/claim")
    public ResponseEntity<ClaimResponse> claim(@PathVariable UUID bracketId) {
        UUID me = caller.currentCaller().orElseThrow();

        ClaimBracket.Claimed claimed = claiming.claim(BracketId.of(bracketId), me);

        return ResponseEntity.ok(new ClaimResponse(
                claimed.bracket().id().value(),
                claimed.bracket().address().describe(),
                claimed.wasMerged(),
                claimed.mergedFrom() == null ? null : claimed.mergedFrom().value()));
    }

    public record ClaimResponse(UUID bracketId, String destination, boolean merged, UUID mergedFrom) {}

    @Operation(
            summary = "Hand work over to somebody else",
            description =
                    """
                    A handover is a close and a re-open, never an edit.

                    Editing the performer on a live bracket is the obvious implementation and it destroys
                    the only thing the graph is for: one bracket would carry two people's work under one
                    duration, and no reader afterwards could tell that the work had moved, when, or how
                    long each person actually held it.

                    The work type is inherited, never re-derived from the new person's role. A designer
                    taking over a video must not relabel it as design work — the business did video, and
                    the graph has to keep saying so however many people touched it.

                    A handover is NOT a completion. Anybody waiting on this work is re-targeted onto the
                    successor rather than told it arrived, however long the chain, and the wait is
                    satisfied exactly once, at the eventual delivery. Telling a waiter that work nobody
                    has done has arrived would stop their clock and make every duration measured
                    afterwards wrong, with nothing anywhere showing it.

                    When the handover happens because somebody left, the successor is marked disrupted
                    and excluded from pattern evidence: a business should not learn a lesson from its own
                    staff turnover. Work that took three weeks because the person doing it left is not
                    evidence that the work takes three weeks.

                    Naming somebody with no active membership produces no bracket at all — an orphan for a
                    manager to place. Inventing a successor for an absent person would create an
                    obligation nobody holds.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Handed over. Says how many waits re-targeted."),
        @ApiResponse(responseCode = "204", description = "Nobody active to hand to; the mark is an orphan."),
        @ApiResponse(responseCode = "403", description = "The caller does not hold WORK_NODE_MARK.")
    })
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    @PostMapping("/{bracketId}/hand-over")
    public ResponseEntity<HandoverResponse> handOver(
            @PathVariable UUID bracketId, @RequestBody HandoverRequest request) {
        return handovers
                .handOver(
                        BracketId.of(bracketId),
                        request.newPerformerId(),
                        oldestNodeOn(request.messageId()),
                        request.causedByDeactivation())
                .map(done -> ResponseEntity.ok(new HandoverResponse(
                        done.closed().value(),
                        done.successor().id().value(),
                        done.successor().address().workType(),
                        done.successor().isDisrupted(),
                        done.outcome().reTargeted().size(),
                        done.outcome().released().size())))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    public record HandoverRequest(UUID newPerformerId, UUID messageId, boolean causedByDeactivation) {}

    public record HandoverResponse(
            UUID closedBracketId,
            UUID successorBracketId,
            String workType,
            boolean disrupted,
            int reTargeted,
            int released) {}

    @Operation(
            summary = "Whether this engagement could be closed",
            description =
                    """
                    A job is ready to close when every piece of work in it has ended. The boundary does
                    not count against itself — it is the container, and a container waiting for itself to
                    finish would mean no engagement could ever end.

                    Recomputed on demand rather than kept as a flag. A flag would need updating from every
                    close path in the system, and the one that forgot would leave an engagement
                    permanently unable to close with nothing to show why.

                    It also reopens: new work marked against a ready job makes it open again, because one
                    late message from a client is a follow-up rather than a second engagement.

                    The count is a number, never a list of who holds what. No figure in this zone is keyed
                    to a person.
                    """)
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Whether it is ready, and what is in the way.")})
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    @GetMapping("/job/{jobId}/readiness")
    public ResponseEntity<CloseJob.Readiness> readiness(@PathVariable UUID jobId) {
        return ResponseEntity.ok(readiness.reconsider(JobId.of(jobId)));
    }

    @Operation(
            summary = "End an engagement",
            description =
                    """
                    Closing writes a JOB_END on the job's boundary, and the root finally has its end.
                    Without closure there is no shape, and without a shape there is no discovery — this is
                    the door the whole observing zone sits behind.

                    Refused while any work is still live. A close that quietly terminalised open work
                    would be a force close performed without a reason and attributed to nobody, and the
                    shape it produced would be marked as evidence with no record that the graph had been
                    cut off.

                    The boundary cannot be closed directly through any route. Closing the boundary IS
                    closing the job; allowing it separately would produce a terminal root under a live
                    engagement.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ended. The boundary carries the job's ending."),
        @ApiResponse(responseCode = "409", description = "Work is still live, or the job already ended.")
    })
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    @PostMapping("/job/{jobId}/close")
    public ResponseEntity<CloseJob.Ended> closeJob(@PathVariable UUID jobId) {
        UUID me = caller.currentCaller().orElseThrow();
        return ResponseEntity.ok(readiness.close(JobId.of(jobId), me));
    }

    @Operation(
            summary = "Force an engagement to end",
            description =
                    """
                    Some engagements end without finishing: a client pulls the budget, scope moves so far
                    it becomes a different piece of work, or a job was opened by mistake. The realistic
                    stuck job is not a silent one — silence lapses quietly. It is endless activity, whose
                    bracket stays alive by right because lapsing somebody who kept working would delete
                    live work from the evidence. Only this ends that.

                    The reason is mandatory. One without it cannot be explained later and cannot be told
                    apart from a mistake, and this is exactly the event somebody asks about months
                    afterwards.

                    The job owner may do this and nobody else. It creates no bottleneck, because
                    force-closure is never urgent: an engagement that should be killed loses nothing by
                    staying open another day.

                    Published outputs survive — the work really was delivered, and this concerns the
                    container rather than what came out of it. The shape does not: the graph is truncated,
                    so the engagement is permanently excluded from teaching the business anything.

                    Everybody holding live work is returned so they can be told once. They were working on
                    something that no longer exists.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Forced closed, with everybody who needs telling."),
        @ApiResponse(responseCode = "400", description = "No reason was given."),
        @ApiResponse(responseCode = "403", description = "The caller does not own this job."),
        @ApiResponse(responseCode = "409", description = "The job already ended.")
    })
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    @PostMapping("/job/{jobId}/force-close")
    public ResponseEntity<CloseJob.Ended> forceCloseJob(
            @PathVariable UUID jobId, @RequestBody ForceCloseRequest request) {
        UUID me = caller.currentCaller().orElseThrow();
        return ResponseEntity.ok(readiness.forceClose(JobId.of(jobId), me, request.reason()));
    }

    public record ForceCloseRequest(String reason) {}

    @Operation(
            summary = "Declare a wait",
            description =
                    """
                    Somebody says what they are waiting for. Waiting silences the person who is blocked and
                    surfaces them to the person who can unblock them — which is a rule about direction
                    rather than about timing.

                    A bracket may hold several waits at once and releases only when all of them are
                    satisfied. Only a real completion satisfies one; a wait declared on work that already
                    delivered is satisfied the moment it is created, so that nobody waits for something
                    that arrived last week.

                    The reason is the person's own words. A dropdown of categories produces a tidy dataset
                    and loses the only sentence anybody would find useful three weeks later.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Declared. May already be satisfied, if the work landed."),
        @ApiResponse(responseCode = "400", description = "The wait would cross a job, or wait on itself.")
    })
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    @PostMapping("/{bracketId}/wait")
    public ResponseEntity<WaitResponse> declareWait(@PathVariable UUID bracketId, @RequestBody WaitRequest request) {
        var wait = waiting.declareFor(
                caller.currentCaller().orElseThrow(),
                BracketId.of(bracketId),
                WaitKind.valueOf(request.kind()),
                request.onBracketId() == null ? null : BracketId.of(request.onBracketId()),
                request.reason(),
                request.expectedBy());

        return ResponseEntity.ok(new WaitResponse(
                wait.id(),
                wait.kind().name(),
                wait.isExternal(),
                wait.satisfiedAt().isPresent()));
    }

    @Operation(
            summary = "It arrived — clear a wait",
            description =
                    """
                    The person carrying the blocked work says the thing they were waiting for came.

                    Until this existed a wait had no way to end. A wait naming a bracket clears when that
                    bracket closes; a CLIENT or SUPPLIER wait names nothing in the graph, because the thing
                    being waited for is an email or a phone call — so it stayed WAITING for ever, N5 and N6
                    kept firing, and the only escape was force-closing the whole engagement.

                    It does not weaken R7.6. That rule forbids the SYSTEM inferring arrival from a close
                    that was not a completion — a handover, a drop, a lapse, a cadence. This is a person
                    stating a completion the system could not observe, which machine 7.4 draws explicitly as
                    the second arrow into Satisfied.

                    Refused on a wait that names a bracket: if the awaited work is in the graph, its own
                    close is what satisfies it, and letting a waiter assert arrival by hand would release
                    them from work that is still open.

                    Only the holder of the blocked bracket may do this. It is their work, their judgement
                    and their clock.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cleared. The bracket releases if nothing else blocks it."),
        @ApiResponse(responseCode = "403", description = "The caller does not hold the blocked work."),
        @ApiResponse(responseCode = "409", description = "Already satisfied or withdrawn, or it names a bracket."),
        @ApiResponse(responseCode = "422", description = "No such wait.")
    })
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    @PostMapping("/wait/{waitId}/arrived")
    public ResponseEntity<Void> waitArrived(@PathVariable UUID waitId) {
        waiting.itArrived(waitId, caller.currentCaller().orElseThrow());
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Stop waiting — withdraw a wait",
            description =
                    """
                    They found another way round it, or it stopped mattering.

                    Withdrawing is NOT satisfying, and the difference is the whole reason both exist.
                    Nothing arrived, so nothing downstream may be measured as though it had: the wait is
                    cancelled rather than closed as a completion, and the wait-dominated analysis reads it
                    as a dependency that was abandoned rather than one that was met.

                    Recording an arrival as a withdrawal is the quiet lie in the other direction — it files
                    every successful external wait as a failure — which is why the two are separate doors
                    rather than one with a flag.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Withdrawn. Nothing is measured as having arrived."),
        @ApiResponse(responseCode = "403", description = "The caller does not hold the blocked work."),
        @ApiResponse(responseCode = "409", description = "Already satisfied or withdrawn."),
        @ApiResponse(responseCode = "422", description = "No such wait.")
    })
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    @PostMapping("/wait/{waitId}/withdraw")
    public ResponseEntity<Void> withdrawWait(@PathVariable UUID waitId) {
        waiting.withdraw(waitId, caller.currentCaller().orElseThrow());
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "What this conversation keeps to hand",
            description =
                    """
                    The shelf: the link everybody re-asks for, the address that lives in one person's
                    head, the wording nobody can find twice.

                    References only — TEXT, LINK or MESSAGE_REF. The product refuses file storage, so
                    there is no upload here and never will be: no bucket, no scanning, no retention
                    policy, and nothing to reach when somebody asks to be erased. What a stored thing
                    needs is to be findable, and a reference is that.

                    Readable by exactly the people who can read the thread, which falls out of the
                    conversation scoping rather than needing a second check.
                    """)
    @ApiResponses({@ApiResponse(responseCode = "200", description = "What is on the shelf now.")})
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    @GetMapping("/conversation/{conversationId}/shelf")
    public ResponseEntity<List<ConversationShelfPort.ShelfItem>> shelf(@PathVariable UUID conversationId) {
        return ResponseEntity.ok(shelf.shelfOf(conversationId));
    }

    @Operation(
            summary = "Put something on the shelf",
            description =
                    """
                    A label is optional, because the commonest case is pasting a link and moving on —
                    demanding a name would make the shelf slower than the habit it replaces.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Placed."),
        @ApiResponse(responseCode = "400", description = "Not one of the three reference kinds.")
    })
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    @PostMapping("/conversation/{conversationId}/shelf")
    public ResponseEntity<Void> place(@PathVariable UUID conversationId, @RequestBody ShelfRequest request) {
        UUID me = caller.currentCaller().orElseThrow();

        shelf.place(conversationId, request.kind(), request.value(), request.label(), me, clock.instant());

        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Take something off the shelf",
            description =
                    """
                    Marked removed rather than deleted. Nothing in this zone is destroyed, and a shelf
                    that silently loses things is one nobody trusts enough to use.
                    """)
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Removed, or already was.")})
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    @PostMapping("/shelf/{itemId}/remove")
    public ResponseEntity<Void> removeFromShelf(@PathVariable UUID itemId) {
        shelf.remove(itemId, clock.instant());
        return ResponseEntity.ok().build();
    }

    public record ShelfRequest(String kind, String value, String label) {}

    public enum RequestedEnd {
        DELIVERED,
        DONE,
        DROPPED
    }

    public record CloseRequest(RequestedEnd kind, String outputKind, String outputValue, String reason) {}

    public record CloseResponse(UUID bracketId, String endedAs, int released, int warned, int childrenClosed) {}

    public record WaitRequest(String kind, UUID onBracketId, String reason, Instant expectedBy) {}

    public record WaitResponse(UUID waitId, String kind, boolean external, boolean alreadySatisfied) {}

    public record MarkRequest(UUID jobId, UUID conversationId, UUID messageId, UUID performerId, String workType) {}

    private static OutputKind outputKind(String named) {
        if (named == null || named.isBlank()) {
            return null;
        }
        try {
            return OutputKind.valueOf(named);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private WorkNodeId nodeFor(UUID messageId, JobId job, UUID performerId) {
        return graph.findMarkOf(messageId, job, performerId, NodeKind.WORK)
                .map(WorkNode::id)
                .orElseGet(() -> oldestNodeOn(messageId));
    }

    private WorkNodeId oldestNodeOn(UUID messageId) {
        return jdbc
                .query(
                        """
                        select work_node_id from work_node_evidence
                        where message_id = ?
                        order by added_at, id
                        limit 1
                        """,
                        (rs, row) -> new WorkNodeId(rs.getObject("work_node_id", UUID.class)),
                        messageId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "message " + messageId + " is not yet a unit of work; mark it first"));
    }

    public record MarkResponse(UUID bracketId, boolean joined, String destination, String workType) {}
}
