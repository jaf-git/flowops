package com.flowops.aiexport.seed;

import com.flowops.auth.infrastructure.session.SessionPrincipal;
import com.flowops.discovery.application.claimbracket.ClaimBracket;
import com.flowops.discovery.application.closebracket.CloseBracket;
import com.flowops.discovery.application.closebracket.CloseOutcome;
import com.flowops.discovery.application.closejob.CloseJob;
import com.flowops.discovery.application.declarewait.DeclareWait;
import com.flowops.discovery.application.handover.HandOverBracket;
import com.flowops.discovery.application.markintobracket.ComposeAddress;
import com.flowops.discovery.application.markintobracket.PlaceMarkInBracket;
import com.flowops.discovery.application.openjob.OpenJobUseCase;
import com.flowops.discovery.application.shared.WorkCapture;
import com.flowops.discovery.application.shared.port.WorkBracketPort;
import com.flowops.discovery.application.shared.port.WorkGraphPort;
import com.flowops.discovery.domain.enums.BracketState;
import com.flowops.discovery.domain.enums.CloseKind;
import com.flowops.discovery.domain.enums.Direction;
import com.flowops.discovery.domain.enums.NodeKind;
import com.flowops.discovery.domain.enums.OutputKind;
import com.flowops.discovery.domain.enums.WaitKind;
import com.flowops.discovery.domain.model.BracketAddress;
import com.flowops.discovery.domain.model.BracketId;
import com.flowops.discovery.domain.model.Job;
import com.flowops.discovery.domain.model.JobId;
import com.flowops.discovery.domain.model.MarkOutcome;
import com.flowops.discovery.domain.model.WorkBracket;
import com.flowops.discovery.domain.model.WorkNodeId;
import com.flowops.discovery.domain.model.WorkNodeWait;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
public class SeedEngagements {
    private static final Logger LOG = LoggerFactory.getLogger(SeedEngagements.class);

    private static final int CAMPAIGN_DAYS = 27;

    private final OpenJobUseCase openJobs;
    private final PlaceMarkInBracket marking;
    private final ComposeAddress addresses;
    private final DeclareWait waiting;
    private final CloseBracket closing;
    private final CloseJob closingJobs;
    private final ClaimBracket claiming;
    private final HandOverBracket handovers;
    private final WorkCapture capture;
    private final WorkBracketPort brackets;
    private final WorkGraphPort jobs;
    private final JdbcTemplate jdbc;
    private final MutableClock clock;

    private Instant campaignStart;

    public SeedEngagements(
            OpenJobUseCase openJobs,
            PlaceMarkInBracket marking,
            ComposeAddress addresses,
            DeclareWait waiting,
            CloseBracket closing,
            CloseJob closingJobs,
            ClaimBracket claiming,
            HandOverBracket handovers,
            WorkCapture capture,
            WorkBracketPort brackets,
            WorkGraphPort jobs,
            JdbcTemplate jdbc,
            MutableClock clock) {
        this.openJobs = openJobs;
        this.marking = marking;
        this.addresses = addresses;
        this.waiting = waiting;
        this.closing = closing;
        this.closingJobs = closingJobs;
        this.claiming = claiming;
        this.handovers = handovers;
        this.capture = capture;
        this.brackets = brackets;
        this.jobs = jobs;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public String walk() {
        Cast cast = readCast();
        if (cast.incomplete()) {
            return "the workspace roster is not seeded yet; no engagements written";
        }

        Chats chats = readChats(cast);
        Messages messages = new Messages(jdbc);

        Instant restoreTo = clock.instant();
        campaignStart = restoreTo.minus(Duration.ofDays(CAMPAIGN_DAYS));

        try {
            Result summerMenu = theSummerMenu(cast, chats, messages);
            Result autumnRefresh = theWorkStillRunning(cast, chats, messages);
            Result pitch = thePitchThatDies(cast, chats, messages);
            Result retainer = theRetainer(cast, chats, messages);

            String summary = "summer-menu=" + summerMenu + " autumn-refresh=" + autumnRefresh + " pitch=" + pitch
                    + " retainer=" + retainer;

            LOG.info("discovery engagements seed: {}", summary);
            return summary;
        } finally {
            clock.set(restoreTo);
            SecurityContextHolder.clearContext();
        }
    }

    private Result theSummerMenu(Cast cast, Chats chats, Messages messages) {
        at(0, 9);
        JobId job = openJobAs(cast.owner(), cast.ownerEmail(), chats.ownerIonut(), messages, "Summer menu");

        at(0, 10);
        WorkBracket brief = open(job, chats.ownerIonut(), cast.owner(), cast.ionut(), messages);
        WorkBracket presentation = open(job, chats.ownerIonut(), cast.owner(), cast.owner(), messages);

        at(0, 11);
        WorkBracket intake = open(job, chats.ownerIoana(), cast.owner(), cast.ioana(), messages);
        WorkBracket proofread = open(job, chats.ownerIoana(), cast.owner(), cast.owner(), messages);

        at(0, 12);
        WorkBracket captions = open(job, chats.ownerAndrei(), cast.owner(), cast.andrei(), messages);
        WorkBracket retro = open(job, chats.ownerAndrei(), cast.owner(), cast.owner(), messages);

        at(0, 14);
        WorkBracket adsSetup = open(job, chats.ownerCosmin(), cast.owner(), cast.cosmin(), messages);
        WorkBracket musicLicence = open(job, chats.ownerCosmin(), cast.owner(), cast.owner(), messages);

        at(0, 15);
        WorkBracket monthlyReport = open(job, chats.ownerDaria(), cast.owner(), cast.daria(), messages);
        WorkBracket printCards = open(job, chats.ownerDaria(), cast.owner(), cast.owner(), messages);

        at(1, 9);
        WorkBracket captionsHandoff = open(job, chats.ionutAndrei(), cast.ionut(), cast.andrei(), messages);
        WorkBracket ionutCoordination = open(job, chats.ionutAndrei(), cast.ionut(), cast.ionut(), messages);

        at(1, 10);
        WorkBracket adVariants = open(job, chats.ioanaCosmin(), cast.ioana(), cast.cosmin(), messages);
        WorkBracket signOffChase = open(job, chats.ioanaCosmin(), cast.ioana(), cast.ioana(), messages);

        at(1, 11);
        WorkBracket competitorLook = open(job, chats.ioanaDaria(), cast.ioana(), cast.daria(), messages);
        WorkBracket scheduling = open(job, chats.ioanaDaria(), cast.ioana(), cast.ioana(), messages);

        at(1, 14);
        WorkBracket promoVideo = open(job, chats.cosminDaria(), cast.daria(), cast.cosmin(), messages);
        WorkBracket retouchRequests = open(job, chats.cosminDaria(), cast.daria(), cast.daria(), messages);

        refuseUnless(
                brackets.liveWorkIn(job).size() == 18,
                "the summer menu opened " + brackets.liveWorkIn(job).size()
                        + " brackets rather than 18; the density this fixture exists to test is not there");

        at(2, 11);
        joinsOrFail(job, chats.ownerAndrei(), cast.owner(), cast.andrei(), messages, "the scope creep");
        at(3, 16);
        joinsOrFail(job, chats.ownerAndrei(), cast.andrei(), cast.andrei(), messages, "the partial delivery");

        at(4, 10);
        joinsOrFail(job, chats.ioanaCosmin(), cast.ioana(), cast.cosmin(), messages, "the first round");
        at(5, 10);
        joinsOrFail(job, chats.ioanaCosmin(), cast.cosmin(), cast.cosmin(), messages, "the second round");
        at(6, 10);
        joinsOrFail(job, chats.ioanaCosmin(), cast.ioana(), cast.cosmin(), messages, "the third round");

        at(1, 15);
        WorkNodeWait budget =
                waiting.declare(adsSetup.id(), WaitKind.CLIENT, null, "The budget number is not confirmed yet", null);
        WorkNodeWait licence = waiting.declare(
                musicLicence.id(), WaitKind.SUPPLIER, null, "The licensing house has not come back", null);
        WorkNodeWait print = waiting.declare(
                printCards.id(), WaitKind.SUPPLIER, null, "City Print quoted four days for the cards", null);

        at(2, 9);
        waiting.declare(
                adsSetup.id(),
                WaitKind.COLLEAGUE,
                captions.id(),
                "Cannot build the ad sets until the captions are final",
                null);

        at(2, 10);
        waiting.declare(
                scheduling.id(),
                WaitKind.COLLEAGUE,
                promoVideo.id(),
                "Nothing can be scheduled until the promo video lands",
                null);

        at(6, 9);
        waiting.withdraw(budget);
        at(6, 11);
        waiting.withdraw(licence);
        at(7, 9);
        waiting.withdraw(print);

        at(8, 10);
        WorkNodeId successorStart = capturedNode(job, chats.cosminDaria(), cast.daria(), cast.daria(), messages);
        HandOverBracket.Handover moved = handovers
                .handOver(promoVideo.id(), cast.daria(), successorStart, true)
                .orElseThrow(() -> new SeedFailedException(
                        "R14 - the promo video could not be handed on, so the waiter behind it has nothing to "
                                + "re-target onto and the chain this fixture exists to demonstrate is absent"));

        refuseUnless(
                moved.outcome().released().isEmpty(),
                "R14.2 - the handover released a waiter; a handover is not a completion, and the work has "
                        + "neither arrived nor died");
        refuseUnless(
                stateOf(scheduling.id()) == BracketState.WAITING,
                "R14.8 - scheduling stopped waiting when the video changed hands. The work moved; it did not "
                        + "arrive, and releasing the waiter would measure every downstream duration from a "
                        + "delivery that never happened");

        at(3, 17);
        deliver(brief, OutputKind.TEXT, "Brief agreed with the client and written up for the team");
        at(4, 15);
        deliver(intake, OutputKind.TEXT, "Budget confirmed at the number the client first said");
        at(7, 16);
        deliver(competitorLook, OutputKind.TEXT, "Six competitors looked at; two worth copying");

        at(9, 12);
        CloseOutcome captionsDelivered =
                deliver(captions, OutputKind.LINK, "https://drive.example/aurora/summer-menu-captions");
        refuseUnless(
                captionsDelivered.released().size() == 1,
                "D6 - delivering the captions released "
                        + captionsDelivered.released().size()
                        + " waiters rather than the one that was blocked on them; only a completion releases "
                        + "a waiter, and it must release every waiter");

        at(9, 14);
        deliver(captionsHandoff, OutputKind.TEXT, "Final captions handed to design, six posts and six stories");
        at(10, 11);
        deliver(adVariants, OutputKind.LINK, "https://drive.example/aurora/ad-variants-three-sizes");
        at(10, 16);
        done(ionutCoordination);

        at(11, 15);
        CloseOutcome videoLanded =
                deliver(moved.successor(), OutputKind.LINK, "https://drive.example/aurora/promo-video-final-cut");
        refuseUnless(
                videoLanded.released().size() == 1,
                "R14.8 - the re-targeted wait was not satisfied at the delivery. A wait that survives a "
                        + "handover is satisfied exactly once, however long the chain, and this one was "
                        + "satisfied " + videoLanded.released().size() + " times");
        refuseUnless(
                stateOf(scheduling.id()) == BracketState.OPEN,
                "R7.3 - scheduling is still blocked after the only thing it was waiting for arrived");

        at(12, 10);
        deliver(scheduling, OutputKind.TEXT, "Six posts and six stories scheduled from the 1st");
        at(12, 16);
        deliver(adsSetup, OutputKind.LINK, "https://drive.example/aurora/ad-sets-live");
        at(13, 11);
        deliver(monthlyReport, OutputKind.TEXT, "First fortnight: reach up a fifth on the spring campaign");

        at(8, 16);
        done(musicLicence);
        at(9, 9);
        done(printCards);
        at(13, 15);
        done(signOffChase);
        at(14, 10);
        done(proofread);
        at(14, 12);
        done(presentation);
        at(18, 16);
        done(retro);

        at(5, 12);
        drop(retouchRequests, "It was a question, not a piece of work");

        Integer holes = jdbc.queryForObject(
                "select count(*) from work_bracket where job_id = ? and close_kind in ('PARENT_CLOSED','LAPSED')",
                Integer.class,
                job.value());
        refuseUnless(
                holes != null && holes == 0,
                "R18 - " + holes + " brackets in the summer menu were terminalised by something other than "
                        + "the person doing the work. Closing a bracket terminalises no other bracket, and "
                        + "the graph is now missing whole streams while looking entirely plausible");

        at(19, 9);
        CloseJob.Readiness readiness = closingJobs.reconsider(job);
        refuseUnless(
                readiness.ready(),
                "R15.1 - the summer menu still holds " + readiness.liveWork()
                        + " live brackets, so nothing is stranded is exactly what this fixture cannot claim");

        at(19, 10);
        CloseJob.Ended ended = closingJobs.close(job, cast.owner());
        refuseUnless(
                ended.shapeEligible(),
                "R15.5 - the summer menu closed and is not shape-eligible, so the densest engagement in the "
                        + "workspace teaches the discovery layer nothing and every screen behind it stays empty");

        return new Result(1, countBrackets(job), 5, 1);
    }

    private Result theWorkStillRunning(Cast cast, Chats chats, Messages messages) {
        at(20, 9);
        JobId job = openJobAs(cast.owner(), cast.ownerEmail(), chats.ownerIoana(), messages, "Autumn refresh");

        at(20, 10);
        WorkBracket copy = open(job, chats.ownerAndrei(), cast.owner(), cast.andrei(), messages);
        WorkBracket accounts = open(job, chats.ownerIonut(), cast.owner(), cast.ionut(), messages);

        at(20, 11);
        WorkBracket goingSpare = open(job, chats.ownerCosmin(), cast.owner(), null, messages);
        WorkBracket stillGoingSpare = open(job, chats.ownerDaria(), cast.owner(), null, messages);

        at(21, 14);
        ClaimBracket.Claimed taken = claiming.claim(goingSpare.id(), cast.cosmin());
        refuseUnless(
                cast.cosmin().equals(taken.bracket().closureRight()),
                "R2.1 - claiming the work did not move the obligation to finish it, so the person doing it "
                        + "cannot close it and the person who can has no idea when it is done");

        refuseUnless(
                brackets.find(stillGoingSpare.id()).orElseThrow().address().isUnclaimed(),
                "the unclaimed bracket this workspace needs one of is no longer unclaimed");

        at(22, 10);
        waiting.declare(
                accounts.id(),
                WaitKind.CLIENT,
                null,
                "Waiting on the client to say which of the two directions they want",
                null);

        refuseUnless(
                stateOf(accounts.id()) == BracketState.WAITING,
                "invariant 8 - a bracket holding an open wait is not WAITING");
        refuseUnless(
                stateOf(copy.id()) == BracketState.OPEN,
                "the autumn refresh has no open work left, so the queue reads as a business that has "
                        + "finished rather than one that is running");

        return new Result(1, countBrackets(job), 1, 0);
    }

    private Result thePitchThatDies(Cast cast, Chats chats, Messages messages) {
        at(20, 15);
        JobId job = openJobAs(cast.owner(), cast.ownerEmail(), chats.ownerIonut(), messages, "Happy Pets pitch");

        at(20, 16);
        WorkBracket pitchCopy = open(job, chats.ownerAndrei(), cast.owner(), cast.andrei(), messages);
        WorkBracket deck = open(job, chats.ownerCosmin(), cast.owner(), cast.cosmin(), messages);
        WorkBracket teaser = open(job, chats.ownerDaria(), cast.owner(), cast.daria(), messages);

        at(22, 11);
        deliver(pitchCopy, OutputKind.TEXT, "Pitch copy written, three pages, sent to the prospect");

        at(23, 10);
        joinsOrFail(job, chats.ownerCosmin(), cast.cosmin(), cast.cosmin(), messages, "still iterating, week one");
        at(24, 10);
        joinsOrFail(job, chats.ownerCosmin(), cast.cosmin(), cast.cosmin(), messages, "still iterating, week two");

        at(24, 14);
        waiting.declare(
                teaser.id(), WaitKind.CLIENT, null, "Waiting for the prospect to say whether they want a teaser", null);

        at(25, 9);
        CloseJob.Readiness stuck = closingJobs.reconsider(job);
        refuseUnless(
                !stuck.ready() && stuck.liveWork() == 2,
                "R15.2 - the pitch reports " + stuck.liveWork() + " live brackets, so a normal close would be "
                        + "permitted on an engagement two people are still working in. Force-closure only earns "
                        + "its existence if the ordinary path genuinely cannot be walked");

        at(25, 10);
        CloseJob.Ended forced =
                closingJobs.forceClose(job, cast.owner(), "The prospect never converted; the pitch is abandoned");

        refuseUnless(
                !forced.shapeEligible(),
                "D12 - the force-closed pitch is shape-eligible, so an engagement with holes in it is about to "
                        + "teach the business a process it never actually ran");
        refuseUnless(
                !forced.tellThem().isEmpty(),
                "R16.5 - nobody is being told the pitch was cancelled, and letting somebody keep working on an "
                        + "engagement that no longer exists is worse than any notification cost");
        refuseUnless(
                deliveredSurvived(pitchCopy.id()),
                "the delivered pitch copy did not stay complete. It really was delivered, and force-closure "
                        + "concerns the container rather than what came out of it");
        refuseUnless(
                stateOf(deck.id()).isTerminal() && stateOf(teaser.id()).isTerminal(),
                "R16 - the force close left live work behind, which is the one thing it exists to prevent");

        return new Result(1, countBrackets(job), 1, 0);
    }

    private Result theRetainer(Cast cast, Chats chats, Messages messages) {
        at(3, 9);
        JobId job = openJobAs(cast.owner(), cast.ownerEmail(), chats.ownerDaria(), messages, "Social retainer");

        Job standing = jobs.findJob(job).orElseThrow();
        standing.isStanding();
        jobs.save(standing);

        int cadences = 0;
        int bereaved = 0;

        for (int week = 0; week < 3; week++) {
            int monday = 3 + (week * 7);

            at(monday, 9);
            WorkBracket posts = open(job, chats.ownerDaria(), cast.owner(), cast.daria(), messages);
            WorkBracket ads = open(job, chats.ownerCosmin(), cast.owner(), cast.cosmin(), messages);

            at(monday + 1, 11);
            joinsOrFail(job, chats.ownerDaria(), cast.daria(), cast.daria(), messages, "the second post of the week");

            if (week == 1) {
                at(monday + 2, 9);
                waiting.declare(
                        ads.id(),
                        WaitKind.COLLEAGUE,
                        posts.id(),
                        "The ad copy repeats this week's posts, so they have to land first",
                        null);
            }

            at(monday + 4, 17);
            CloseOutcome cadence = closing.closedByCadence(posts.id());
            cadences++;
            bereaved += cadence.bereaved().size();

            refuseUnless(
                    cadence.released().isEmpty(),
                    "R6.2 - a cadence close released a waiter. It is administrative: nothing was delivered, "
                            + "and anybody waiting must be told the target died rather than that it arrived");

            at(monday + 4, 18);
            deliver(ads, OutputKind.TEXT, "Ad copy for week " + (week + 1) + " written from the posts that ran");
        }

        refuseUnless(
                bereaved == 1,
                "the retainer produced " + bereaved + " waiters told their target died rather than the one this "
                        + "fixture needs, so the difference between a cadence close and a delivery is not on screen");

        at(25, 11);
        open(job, chats.ownerDaria(), cast.owner(), cast.daria(), messages);

        return new Result(1, countBrackets(job), 1, cadences);
    }

    private JobId openJobAs(UUID person, String email, UUID conversation, Messages messages, String name) {
        UUID message = messages.next(conversation);
        actingAs(person, email);
        try {
            return openJobs.execute(new OpenJobUseCase.OpenJob(message, name, name, null, null, true))
                    .job();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private WorkBracket open(JobId job, UUID conversation, UUID marker, UUID performer, Messages messages) {
        MarkOutcome outcome = place(job, conversation, marker, performer, messages);

        if (!(outcome instanceof MarkOutcome.Opened)) {
            throw new SeedFailedException("a mark meant to open new work joined bracket "
                    + outcome.bracket().id().value() + " instead, so this engagement is one stream short of the "
                    + "density it claims");
        }
        return outcome.bracket();
    }

    private void joinsOrFail(
            JobId job, UUID conversation, UUID marker, UUID performer, Messages messages, String what) {
        MarkOutcome outcome = place(job, conversation, marker, performer, messages);

        if (!(outcome instanceof MarkOutcome.Joined)) {
            throw new SeedFailedException("R1 - " + what + " opened a second bracket at an address that already "
                    + "had one. Joining is the default; one piece of work has just been split in two and every "
                    + "duration read across it is now halved twice");
        }
    }

    private MarkOutcome place(JobId job, UUID conversation, UUID marker, UUID performer, Messages messages) {
        WorkNodeId node = capturedNode(job, conversation, marker, performer, messages);
        BracketAddress address = addresses.composeFor(job, conversation, performer);

        return marking.place(job, address, node, marker);
    }

    private WorkNodeId capturedNode(JobId job, UUID conversation, UUID marker, UUID performer, Messages messages) {
        UUID message = messages.next(conversation);

        Direction direction = performer == null || performer.equals(marker) ? Direction.STANDALONE : Direction.REQUEST;

        return capture.capture(message, marker, job, direction, NodeKind.WORK, performer)
                .node()
                .id();
    }

    private CloseOutcome deliver(WorkBracket bracket, OutputKind kind, String value) {
        return closing.delivered(bracket.id(), kind, value, holderOf(bracket.id()));
    }

    private void done(WorkBracket bracket) {
        closing.done(bracket.id(), holderOf(bracket.id()));
    }

    private void drop(WorkBracket bracket, String reason) {
        closing.dropped(bracket.id(), reason, holderOf(bracket.id()));
    }

    private UUID holderOf(BracketId id) {
        return brackets.find(id).orElseThrow().closureRight();
    }

    private BracketState stateOf(BracketId id) {
        return brackets.find(id).orElseThrow().state();
    }

    private boolean deliveredSurvived(BracketId id) {
        WorkBracket bracket = brackets.find(id).orElseThrow();
        return bracket.closeKind().map(kind -> kind == CloseKind.DELIVERED).orElse(false);
    }

    private int countBrackets(JobId job) {
        Integer count = jdbc.queryForObject(
                "select count(*) from work_bracket where job_id = ? and is_boundary = false",
                Integer.class,
                job.value());
        return count == null ? 0 : count;
    }

    private void at(int day, int hour) {
        clock.set(campaignStart.plus(Duration.ofDays(day)).plus(Duration.ofHours(hour)));
    }

    private void actingAs(UUID person, String email) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                new UsernamePasswordAuthenticationToken(new SessionPrincipal(person, email), null, List.of()));
        SecurityContextHolder.setContext(context);
    }

    private void refuseUnless(boolean held, String whatBroke) {
        if (!held) {
            throw new SeedFailedException(whatBroke);
        }
    }

    private Cast readCast() {
        Map<String, UUID> byEmail = new HashMap<>();
        jdbc.query("select email, id from auth_user", rs -> {
            byEmail.put(rs.getString("email"), rs.getObject("id", UUID.class));
        });

        return new Cast(
                byEmail.get(Cast.OWNER_EMAIL),
                byEmail.get("ionut@atelier.ro"),
                byEmail.get("ioana@atelier.ro"),
                byEmail.get("andrei@atelier.ro"),
                byEmail.get("cosmin@atelier.ro"),
                byEmail.get("daria@atelier.ro"));
    }

    private Chats readChats(Cast cast) {
        return new Chats(
                directChat(cast.owner(), cast.ionut()),
                directChat(cast.owner(), cast.ioana()),
                directChat(cast.owner(), cast.andrei()),
                directChat(cast.owner(), cast.cosmin()),
                directChat(cast.owner(), cast.daria()),
                directChat(cast.ionut(), cast.andrei()),
                directChat(cast.ioana(), cast.cosmin()),
                directChat(cast.ioana(), cast.daria()),
                directChat(cast.cosmin(), cast.daria()));
    }

    private UUID directChat(UUID one, UUID other) {
        return jdbc
                .query(
                        """
                        select p.conversation_id
                        from conversation_participant p
                        join conversation_participant q
                          on q.conversation_id = p.conversation_id and q.person_id = ?
                        where p.person_id = ?
                          and (select count(*) from conversation_participant c
                               where c.conversation_id = p.conversation_id) = 2
                        limit 1
                        """,
                        (rs, row) -> rs.getObject("conversation_id", UUID.class),
                        one,
                        other)
                .stream()
                .findFirst()
                .orElseThrow(() -> new SeedFailedException("there is no direct conversation between " + one + " and "
                        + other + ", so the chat half of the bracket address cannot be produced"));
    }

    static final class Messages {
        private final JdbcTemplate jdbc;
        private final Map<UUID, Deque<UUID>> unspoken = new HashMap<>();

        Messages(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        UUID next(UUID conversation) {
            Deque<UUID> queue = unspoken.computeIfAbsent(conversation, this::load);

            if (queue.isEmpty()) {
                throw new SeedFailedException("conversation " + conversation
                        + " has no unmarked sentence left. The engagement being built is longer than the "
                        + "conversation it stands on, and a mark on an already-marked message would plant a "
                        + "double-mark finding about the seed rather than about the business");
            }
            return queue.removeFirst();
        }

        private Deque<UUID> load(UUID conversation) {
            return new ArrayDeque<>(jdbc.query(
                    """
                    select m.id
                    from message m
                    where m.conversation_id = ?
                      and m.deleted_at is null
                      and not exists (select 1 from work_node_evidence e where e.message_id = m.id)
                    order by m.sent_at, m.seq
                    """,
                    (rs, row) -> rs.getObject("id", UUID.class),
                    conversation));
        }
    }

    private record Cast(UUID owner, UUID ionut, UUID ioana, UUID andrei, UUID cosmin, UUID daria) {
        private static final String OWNER_EMAIL = "maria@atelier.ro";

        String ownerEmail() {
            return OWNER_EMAIL;
        }

        boolean incomplete() {
            return owner == null || ionut == null || ioana == null || andrei == null || cosmin == null || daria == null;
        }
    }

    private record Chats(
            UUID ownerIonut,
            UUID ownerIoana,
            UUID ownerAndrei,
            UUID ownerCosmin,
            UUID ownerDaria,
            UUID ionutAndrei,
            UUID ioanaCosmin,
            UUID ioanaDaria,
            UUID cosminDaria) {}

    private record Result(int jobs, int brackets, int waits, int cadenceCloses) {
        @Override
        public String toString() {
            return brackets + " brackets, " + waits + " waits"
                    + (cadenceCloses > 0 ? ", " + cadenceCloses + " cadence closes" : "");
        }
    }
}
