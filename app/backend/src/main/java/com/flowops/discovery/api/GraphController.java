package com.flowops.discovery.api;

import com.flowops.discovery.application.collaboration.SeeWhoWorkedTogether;
import com.flowops.discovery.application.publicgraph.ViewPublicGraph;
import com.flowops.discovery.application.shared.port.ClientArtifactPort;
import com.flowops.discovery.application.shared.port.CollaborationReadPort;
import com.flowops.discovery.application.shared.port.IdentifyCallerPort;
import com.flowops.discovery.application.shared.port.JobHeaderPort;
import com.flowops.discovery.application.shared.port.MyTrackPort;
import com.flowops.discovery.application.shared.port.MyWaitsPort;
import com.flowops.discovery.application.shared.port.MyWorkCountsPort;
import com.flowops.discovery.application.shared.port.TrackerRailPort;
import com.flowops.discovery.application.shared.port.VocabularyReadPort;
import com.flowops.discovery.application.vocabulary.WatchTheVocabulary;
import com.flowops.discovery.domain.model.JobId;
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
@RequestMapping("/api/discovery/graph")
@Tag(
        name = "Discovery · the workspace graph",
        description = "The shape of the work, searchable, with the words kept where they were said.")
public class GraphController {
    private final ViewPublicGraph graph;
    private final SeeWhoWorkedTogether collaboration;
    private final WatchTheVocabulary vocabulary;
    private final IdentifyCallerPort caller;

    public GraphController(
            ViewPublicGraph graph,
            SeeWhoWorkedTogether collaboration,
            WatchTheVocabulary vocabulary,
            IdentifyCallerPort caller) {
        this.graph = graph;
        this.collaboration = collaboration;
        this.vocabulary = vocabulary;
        this.caller = caller;
    }

    @Operation(
            summary = "The shape of an engagement, for anybody in the workspace",
            description =
                    """
                    The structure is public. The words are not.

                    Nodes, brackets, states, work types, durations and outputs are visible to everybody
                    here. A node's TEXT is not, and this response has nowhere to put one — the rows come
                    from a port with no method that returns a sentence, so a node from a private
                    conversation renders as "design · Karim · delivered · 4h" and there is no code path
                    that could make it render otherwise.

                    That is why there is no permission check on this route. A check would imply the
                    response could carry something worth protecting, and invite somebody to relax it.

                    Attribution is here: rows name who did the work, because that is a fact about a piece
                    of work. No count, rate or ranking is, and no route exists that could produce one.

                    Edges come with the nodes, in three kinds — PARENTAGE, WAIT and SUCCESSION — and NO
                    EDGE CROSSES A JOB. That absence is a feature rather than an omission: a false edge
                    accumulates daily until the system is confident and wrong. It is enforced in the
                    queries, so a cross-job edge is never selected rather than filtered out afterwards.

                    A WAIT edge carries no reason. The reason is free text somebody typed and belongs to
                    its conversation's participants; it is served by the waiting tab and this response has
                    nowhere to put it.

                    DISCOVERY-VIEW-GRAPH-01.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The graph's shape and its edges. No text, by construction."),
        @ApiResponse(responseCode = "401", description = "Not signed in."),
        @ApiResponse(responseCode = "403", description = "Requires WORK_NODE_MARK — membership of this workspace.")
    })
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    @GetMapping("/job/{jobId}")
    public ResponseEntity<ViewPublicGraph.Graph> shapeOf(@PathVariable UUID jobId) {
        return ResponseEntity.ok(graph.graphOf(JobId.of(jobId)));
    }

    @Operation(
            summary = "An engagement's header — client, project, state, and who closes it",
            description =
                    """
                    S1. Everything else in this zone depends on the engagement being reachable without
                    hunting for it, and a pinned bar is what makes it so.

                    liveBrackets is what turns the close control on: a job with live work refuses a normal
                    close (R15.2, T43), and stating the number here is what stops that refusal being a
                    surprise somebody meets by pressing the button. THE JOB'S OWN BOUNDARY IS NOT COUNTED
                    — it is a container, always open until the engagement ends, and counting it would mean
                    no job ever reached zero.

                    totalBrackets is every piece of work the engagement has ever held, and it is here
                    because liveBrackets = 0 answers two opposite questions: work that is all delivered,
                    and work nobody has started. The bar said "Ready to close" for both and offered the
                    accent button on an engagement opened seconds earlier.

                    closeReason is the one free-text string in this response and it is a statement about
                    the engagement rather than a conversation's words: R16 requires a force close to say
                    why and requires the scar to stay visible.

                    No figure here is about a person. liveBrackets counts brackets, which are pieces of
                    work, and there is no route that could count anything else.

                    DISCOVERY-VIEW-JOB-HEADER-01.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The header."),
        @ApiResponse(responseCode = "404", description = "No such engagement — a stale link rather than an error."),
        @ApiResponse(responseCode = "401", description = "Not signed in."),
        @ApiResponse(responseCode = "403", description = "Requires WORK_NODE_MARK — membership of this workspace.")
    })
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    @GetMapping("/job/{jobId}/header")
    public ResponseEntity<JobHeaderPort.JobHeader> headerOf(@PathVariable UUID jobId) {
        return graph.headerOf(JobId.of(jobId)).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound()
                .build());
    }

    @Operation(
            summary = "What this engagement delivered",
            description =
                    """
                    S9, and R6's *feeds forward* made reachable. A delivered output is what lets a
                    terminal end feed the next piece of work without an edge somebody drew: Karim's
                    designs are published, Maya's scheduling waited on them, and the link between the two
                    is a fact rather than an assertion.

                    THE FACT IS EVERYBODY'S; THE VALUE IS NOT. D5 — an artifact's value is readable by
                    the originating conversation's participants and by the job's owner. A row you may not
                    read still comes back, with `readable: false` and a null value, because hiding it
                    would make the product lie about what the engagement produced and showing the link
                    would be the leak. "A design was delivered on the 14th, and you cannot open it from
                    here" is the honest third answer.

                    The narrowing is a CASE in the query. A value you may not read is NEVER SELECTED,
                    rather than selected and blanked by a mapper somebody has to remember to write.

                    `readable` is a flag rather than something to infer from the null, because "we did
                    not fetch this for you" and "this delivery recorded no value" are different facts and
                    a screen should say the right one.

                    DISCOVERY-VIEW-ARTIFACTS-01.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "What was published, newest first."),
        @ApiResponse(responseCode = "401", description = "Not signed in."),
        @ApiResponse(responseCode = "403", description = "Requires WORK_NODE_MARK — membership of this workspace.")
    })
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    @GetMapping("/job/{jobId}/artifacts")
    public ResponseEntity<List<ClientArtifactPort.Artifact>> artifacts(@PathVariable UUID jobId) {
        UUID me = caller.currentCaller().orElseThrow();
        return ResponseEntity.ok(graph.publishedBy(JobId.of(jobId), me));
    }

    @Operation(
            summary = "The tracker rail — every live bracket, grouped by engagement",
            description =
                    """
                    S2, and the persistence surface the whole flow leans on. Without it the only
                    confirmation anybody gets that their mark landed is a ten-second toast, and a zone
                    whose single input is one tap cannot afford for that tap to leave no trace.

                    Now that the graph is public it is also everybody's "what have I got open" screen,
                    and A6 says that visibility is what materially reduces the lapse rate: people close
                    work they can see.

                    LANES ARE KEYED BY CLIENT AND PROJECT, NEVER BY PERSON. A rail with one lane per
                    colleague is the people axis R13.2 refuses, and it is the arrangement a reader
                    reaches for first. The refusal is structural — `Lane` has no field naming a person,
                    so a person-keyed rail cannot be built from this route without changing the type.

                    No counts. A lane holds its marks; a renderer that wants a total counts the list it
                    was given.

                    Live brackets only, and NOT the job's own boundary. The boundary is a container that
                    stays open until the engagement ends, so including it would put a permanent
                    undischargeable mark on every lane — and a rail whose marks are not all actionable
                    stops being scanned.

                    No text. A mark carries its work type, who is doing it, how it stands, and which
                    message to jump to.

                    DISCOVERY-VIEW-RAIL-01.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Every live bracket, by engagement."),
        @ApiResponse(responseCode = "401", description = "Not signed in."),
        @ApiResponse(responseCode = "403", description = "Requires WORK_NODE_MARK — membership of this workspace.")
    })
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    @GetMapping("/rail")
    public ResponseEntity<List<TrackerRailPort.Lane>> rail() {
        return ResponseEntity.ok(graph.openWork());
    }

    @Operation(
            summary = "Your own three numbers — waiting on you, open work, delivered this week",
            description =
                    """
                    ONE READ, ONE ANSWER. Four different counts of live work existed in this product,
                    three of them on screen at once, each computed by whichever client happened to be
                    holding a list. Two tiles built that way disagree the moment one of their lists is
                    filtered, paged or a request behind the other, and nothing on the page says which of
                    the two is wrong. All three figures here come from a single statement over the whole
                    population, so a screen showing them is showing three facts about one moment.

                    EVERY FIGURE IS ABOUT THE VIEWER'S OWN WORK, which is R12.1's permitted form. "Sunrise
                    design: 3 rounds" is a fact about the account; "Karim: 3 rounds" is the same data
                    pointed at a person; "you have 3 things open" is somebody reading their own desk. The
                    route takes no person as a parameter — it cannot be aimed at a colleague — and the
                    response has no field a name could go in.

                    waitingOnYou COUNTS WAITS, NOT WAITERS. Two colleagues blocked on one bracket are two
                    waits; "two people are waiting on you" would be a tally over humans, and that is the
                    one figure this zone may never produce. It reads the AWAITED end of a wait: somebody
                    cannot move until this person delivers. The other end — what you are waiting for — is
                    a different figure and belongs on a different tile.

                    openWork IS NOT THE JOB'S OWN BOUNDARY. R4a.1 makes the boundary a container that
                    stays open until the engagement ends, so counting it would give everybody who has ever
                    opened an engagement a permanent extra piece of work they cannot discharge — and a
                    number nobody can drive to zero is a number people stop reading.

                    deliveredThisWeek counts DELIVERED and DONE only. Two of the nine close kinds are
                    completions; a handover, a drop, a lapse or a cadence close is an ending rather than
                    an achievement, and reporting one here would tell somebody they finished the week's
                    work by abandoning it. The week starts on Monday.

                    DISCOVERY-VIEW-MY-WORK-COUNTS-01.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Three numbers about your own work, and no name."),
        @ApiResponse(responseCode = "401", description = "Not signed in."),
        @ApiResponse(responseCode = "403", description = "Requires WORK_NODE_MARK — membership of this workspace.")
    })
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    @GetMapping("/my-work-counts")
    public ResponseEntity<MyWorkCountsPort.MyWorkCounts> myWorkCounts() {
        UUID me = caller.currentCaller().orElseThrow();
        return ResponseEntity.ok(graph.myWork(me));
    }

    @Operation(
            summary = "The list behind the count — who is blocked, and on what of yours",
            description =
                    """
                    THE LIST AND THE COUNT ARE ONE PREDICATE. /my-work-counts says waitingOnYou is 2; this
                    names those same two. A panel of three rows under a tile reading two is the defect the
                    inventory found four times over — four different counts of live work, three of them on
                    screen at once, each computed by whichever client held a list — and nothing on such a
                    page says which half is lying. Both reads narrow identically: the AWAITED end of an
                    open, uncancelled wait, scoped to the caller.

                    ON… IS YOUR WORK, WAITING… IS THEIRS. `onBracketId` and `onAddress` are what is wanted
                    — the thing whose delivery ends this wait. `waitingBracketId` and `waitingAddress` are
                    what is held up meanwhile. Read the wrong way round the list answers a different
                    question of roughly the same size — what YOU are waiting for — which is a real figure
                    and belongs on a different tile.

                    OLDEST FIRST. The longest-blocked thing is the one to act on, and a newest-first list
                    puts this morning's wait above the one that has held somebody up since Tuesday.

                    THE NAME IS ATTRIBUTION AND NOTHING ELSE. R12.1 — `waitingPerformerName` appears once
                    per row because knowing who is held up is what makes a row actionable. There is no
                    count about them, no ranking, no grouping and no ordering by them, and the route takes
                    no person as a parameter, so it cannot be aimed at a colleague.

                    THERE IS NO REASON FIELD. R13.1 — a wait's reason is free text somebody typed in a
                    conversation this caller may not be in. It is not in the response and not in the
                    query. The address, the kind and the timing are structure and are everybody's.

                    `declaredAt` IS AN INSTANT, NEVER A DURATION. R12.5 — a duration shown without naming
                    its phase invites somebody to read a client's five-day silence as a colleague's
                    slowness, so the moment is sent and the phase is named beside it by whoever renders
                    it.

                    DISCOVERY-VIEW-MY-WAITS-01.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The open waits aimed at your work, oldest first."),
        @ApiResponse(responseCode = "401", description = "Not signed in."),
        @ApiResponse(responseCode = "403", description = "Requires WORK_NODE_MARK — membership of this workspace.")
    })
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    @GetMapping("/my-waits")
    public ResponseEntity<List<MyWaitsPort.WaitOnMe>> myWaits() {
        UUID me = caller.currentCaller().orElseThrow();
        return ResponseEntity.ok(graph.waitsOnMe(me));
    }

    @Operation(
            summary = "Your own work, as the chains of nodes it actually is",
            description =
                    """
                    ONE LINE PER PIECE OF WORK YOU PERFORM, and inside it that work's own nodes oldest
                    first — the chain as it happened. R4.2 drawn literally: a START, a WORK node for each
                    step since, and the END generated when somebody closed it. A START is never mutated
                    into an END, so a bracket that opened and closed on one sentence still has two nodes
                    and is still distinguishable from work that never finished.

                    THIS IS THE FIRST READ THAT SURFACES work_node.paired_node_id. Either end of a pair
                    reads the other, which is what makes "what ended this?" answerable from the start of a
                    chain rather than by a scan.

                    SCOPED BY THE SESSION, AND IT TAKES NO PARAMETER. R12.1 — there is nothing here to aim
                    at a colleague. Append `?performerId=…` and nothing changes: the route has nowhere to
                    put it. Sign in as somebody else, which is the only way to see another answer.

                    NO NAME, NO COUNT, NO RANKING AND NO DURATION. Every line is already yours, so naming
                    you would be the product telling you who you are; and R12.5 — a duration shown without
                    naming its phase invites somebody to read a client's five-day silence as a colleague's
                    slowness. There is no timestamp either: the ordering is done in SQL and the moments
                    stay in the database.

                    NO TEXT. R13.1 — a node's `messageId` is a pointer the client resolves through a read
                    that already checks who may see it. The sentence is never selected. `messageId` is
                    NULL where a node has no evidence of its own, and null is a real answer rather than a
                    placeholder: the circle is drawn and is simply not a way back.

                    THE ENGAGEMENT'S OWN BOUNDARY IS NOT HERE. R4a.1 makes it a container that stays open
                    until the engagement ends, and a container is not something somebody is carrying —
                    which is this page's whole subject. Included, everybody who has ever opened an
                    engagement would carry a permanent line they can neither work on nor discharge.

                    CLOSED WORK IS HERE. A chain that ended is the only kind with an END to show; dropping
                    it would answer "what am I doing now", which is /rail's question.

                    DISCOVERY-VIEW-MY-TRACK-01.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Your own chains, live work first, nodes oldest first."),
        @ApiResponse(responseCode = "401", description = "Not signed in."),
        @ApiResponse(responseCode = "403", description = "Requires WORK_NODE_MARK — membership of this workspace.")
    })
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    @GetMapping("/my-track")
    public ResponseEntity<List<MyTrackPort.TrackLine>> myTrack() {
        UUID me = caller.currentCaller().orElseThrow();
        return ResponseEntity.ok(graph.myTrack(me));
    }

    @Operation(
            summary = "Search the work",
            description =
                    """
                    Matches in conversations you already read come back with their words. Matches in
                    conversations you do not come back as a NUMBER: "3 results in conversations you
                    cannot see."

                    Saying nothing about them would make the product lie about what it holds. Saying what
                    they are would be the leak. A count is the only honest third answer — it tells you to
                    go and ask a colleague rather than to conclude the thing does not exist.

                    The readable half is narrowed by your participation INSIDE the query, not filtered
                    after it. The rows you may not read are never fetched, so there is no moment at which
                    they exist in memory one mapper away from a response.

                    There is no way to search for a person. "Everything Karim said" is a person-keyed
                    query wearing a search box.
                    """)
    @ApiResponses({@ApiResponse(responseCode = "200", description = "What you may read, and how much you may not.")})
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    @GetMapping("/search")
    public ResponseEntity<ViewPublicGraph.Found> search(@RequestParam String q) {
        UUID me = caller.currentCaller().orElseThrow();
        return ResponseEntity.ok(graph.search(q, me));
    }

    @Operation(
            summary = "Who was doing one thing together",
            description =
                    """
                    A bracket never has two performers. The performer is part of the address precisely so
                    that two writers under one manager stay separate — so two people on one deliverable
                    are always two brackets, and collaboration is those brackets grouped by evidence.

                    Three tiers, and the tier decides what may be asserted. STRONG: both published the
                    same output — they made one thing. GOOD: their starts share one message AND the same
                    work type — they were asked for it together. WEAK: same job, same work type, nothing
                    shared.

                    Only STRONG and GOOD collapse to one step in a learned process. WEAK is drawn as a
                    band and never collapsed: two collaborators who each joined their own bracket can end
                    up with different messages and different outputs, so same-job-same-type is all that
                    remains — real enough to draw, too thin to assert. Collapsing on it would put a step
                    in the learned process that never existed.

                    The evidence travels with each group, so a person can see WHY the system thinks these
                    are one piece of work rather than being asked to trust it.
                    """)
    @ApiResponses({@ApiResponse(responseCode = "200", description = "The groups, with their tier and evidence.")})
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    @GetMapping("/job/{jobId}/collaboration")
    public ResponseEntity<List<SeeWhoWorkedTogether.Group>> collaboration(@PathVariable UUID jobId) {
        return ResponseEntity.ok(collaboration.inJob(JobId.of(jobId)));
    }

    @Operation(
            summary = "Assets that served two engagements",
            description =
                    """
                    The same output value appearing in two jobs means one asset served two engagements.

                    NO EDGE IS DRAWN — the outer wall holds, and a dependency inferred across jobs is
                    exactly the false relationship this zone refuses to invent. But it is reported,
                    because reuse is a real economic fact that is otherwise invisible: round two of the
                    summer menu costing half of round one is explained entirely by which assets came for
                    free, and without this the difference is inexplicable.
                    """)
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Outputs that appeared in more than one job.")})
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    @GetMapping("/reuse")
    public ResponseEntity<List<CollaborationReadPort.Reuse>> reuse() {
        return ResponseEntity.ok(collaboration.assetsThatCameForFree());
    }

    @Operation(
            summary = "Has anybody used this word before?",
            description =
                    """
                    What the work-type chip says while somebody is still typing: "nobody has used
                    RESEARCH before", with the closest existing types offered.

                    Prevention at the point of creation, and NEVER a gate. Anyone may create a work type
                    and it is never blocked — restricting it to the owner would block work in order to
                    protect a taxonomy, which inverts the priority. The safeguard is visibility, not
                    permission.

                    What it prevents is fragmentation: one person writing RESEARCH and another writing
                    COMPETITOR_ANALYSIS for the same activity splits the taxonomy, and process discovery
                    breaks — the same process appears as two, each with half the evidence, and neither
                    reaches the sample floor. Nothing errors. The product just stops finding something it
                    used to find.

                    Told afterwards in a weekly, the split has already happened.
                    """)
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Whether it is new, and its nearest neighbours.")})
    @PreAuthorize("hasAuthority('WORK_NODE_MARK')")
    @GetMapping("/work-type")
    public ResponseEntity<VocabularyReadPort.Familiarity> workType(@RequestParam String name) {
        return ResponseEntity.ok(vocabulary.aboutToUse(name));
    }

    @Operation(
            summary = "This week's vocabulary changes",
            description =
                    """
                    New work types with their first use, new counterparties, new projects, and any two
                    rare types that resemble each other closely enough to be worth a look.

                    Ambient, never urgent, and NEVER a notification. Nobody needs to do anything about a
                    new work type today, and sending it as a notice would spend the notification budget
                    on something that belongs in a digest read on a Monday morning.

                    Attribution appears — "RESEARCH, first used by Rana" — and no count per person does.
                    A leaderboard of who introduces the most types is a person-keyed figure wearing a
                    different hat.

                    Merge suggestions are suggestions. Merging a company's vocabulary is a decision, not
                    a cleanup: two similar words may be one activity, or the distinction a team's whole
                    method rests on, and only a person knows which.
                    """)
    @ApiResponses({@ApiResponse(responseCode = "200", description = "New words, new clients, new projects.")})
    @PreAuthorize("hasAuthority('DISCOVERY_CANVAS_VIEW')")
    @GetMapping("/vocabulary")
    public ResponseEntity<WatchTheVocabulary.Weekly> vocabulary() {
        return ResponseEntity.ok(vocabulary.thisWeek());
    }
}
