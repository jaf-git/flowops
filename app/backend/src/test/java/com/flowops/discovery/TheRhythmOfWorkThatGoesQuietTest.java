package com.flowops.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowops.discovery.application.rhythm.BracketRhythm;
import com.flowops.discovery.application.shared.port.WorkBracketPort;
import com.flowops.discovery.domain.enums.BracketState;
import com.flowops.discovery.domain.enums.CloseKind;
import com.flowops.discovery.domain.enums.WaitKind;
import com.flowops.discovery.domain.model.BracketAddress;
import com.flowops.discovery.domain.model.JobId;
import com.flowops.discovery.domain.model.RhythmWindows;
import com.flowops.discovery.domain.model.WorkBracket;
import com.flowops.discovery.domain.model.WorkNodeId;
import com.flowops.discovery.domain.model.WorkNodeWait;
import com.flowops.support.ApplicationTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class TheRhythmOfWorkThatGoesQuietTest extends ApplicationTest {
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private BracketRhythm rhythm;

    @Autowired
    private WorkBracketPort brackets;

    private static final RhythmWindows WINDOWS =
            new RhythmWindows(Duration.ofHours(3), Duration.ofHours(3), Duration.ofDays(7), Duration.ofDays(30));

    private UUID maria;
    private UUID andrei;
    private UUID conversation;

    @BeforeEach
    void anAtelierWithTwoPeopleInOneChannel() {
        maria = person("maria");
        andrei = person("andrei");
        conversation = UUID.randomUUID();
    }

    @Test
    @DisplayName("R8.1 — a bracket nudged and then worked on does not lapse; the one beside it that stayed silent does")
    void activityAfterANudgeCancelsTheLapse() {
        JobId job = job("Aurora Coffee · autumn campaign", maria, "OPEN", false);
        Instant nudgedAt = Instant.now().minus(Duration.ofDays(2));

        WorkBracket andreisPhotos = openBracket(job, "PHOTO", andrei, nudgedAt.minus(Duration.ofDays(1)));
        andreisPhotos.nudged(nudgedAt);
        andreisPhotos.touched(nudgedAt.plus(Duration.ofHours(6)));
        brackets.save(andreisPhotos);

        WorkBracket mariasInvoices = openBracket(job, "FINANCE", maria, nudgedAt.minus(Duration.ofDays(1)));
        mariasInvoices.nudged(nudgedAt);
        brackets.save(mariasInvoices);

        rhythm.lapseTheSilent(WINDOWS);

        WorkBracket photos = brackets.find(andreisPhotos.id()).orElseThrow();
        assertThat(photos.state().isTerminal())
                .describedAs("R8.1 - Andrei kept working; lapsing him deletes live work from the evidence")
                .isFalse();
        assertThat(photos.closeKind())
                .describedAs("a bracket that did not lapse ended in no way at all")
                .isEmpty();

        assertThat(brackets.find(mariasInvoices.id()).orElseThrow().closeKind())
                .describedAs("R8 - silence after the one nudge is what a lapse is for")
                .contains(CloseKind.LAPSED);
    }

    @Test
    @DisplayName(
            "R8.2 — a holder active anywhere in the workspace keeps their queue; the one who stopped appearing lapses")
    void aQueueDoesNotLapseWhileItsHolderIsStillWorking() {
        JobId job = job("Aurora Coffee · autumn campaign", maria, "OPEN", false);
        Instant nudgedAt = Instant.now().minus(Duration.ofDays(2));
        Instant afterwards = nudgedAt.plus(Duration.ofHours(6));

        UUID sara = person("sara");
        UUID karim = person("karim");
        UUID nour = person("nour");

        WorkBracket andreisPhotos = nudgedAndUntouchedSince(job, "PHOTO", andrei, nudgedAt);
        WorkBracket sarasCopy = nudgedAndUntouchedSince(job, "CONTENT", sara, nudgedAt);
        WorkBracket karimsAds = nudgedAndUntouchedSince(job, "ADS", karim, nudgedAt);
        WorkBracket noursVideo = nudgedAndUntouchedSince(job, "VIDEO", nour, nudgedAt);
        WorkBracket mariasInvoices = nudgedAndUntouchedSince(job, "FINANCE", maria, nudgedAt);

        saidSomethingInTheChannel(andrei, afterwards);
        markedAMessageAsWork(job, sara, afterwards);

        WorkBracket karimsBrief = openBracket(job, "BRIEF", karim, nudgedAt.minus(Duration.ofDays(1)));
        karimsBrief.done(node(job, afterwards), afterwards);
        brackets.save(karimsBrief);

        WorkBracket noursEdit = openBracket(job, "EDIT", nour, nudgedAt.minus(Duration.ofDays(1)));
        brackets.save(WorkNodeWait.declared(
                brackets.nextWaitId(),
                noursEdit,
                WaitKind.CLIENT,
                null,
                "waiting on the client's footage",
                null,
                afterwards));

        rhythm.lapseTheSilent(WINDOWS);

        assertThat(brackets.find(andreisPhotos.id()).orElseThrow().closeKind())
                .describedAs("R8.2 - Andrei was talking in the channel six hours after the nudge; he is here")
                .isEmpty();
        assertThat(brackets.find(sarasCopy.id()).orElseThrow().closeKind())
                .describedAs("R8.2 - Sara marked a message as work; her queue is a queue, not an abandonment")
                .isEmpty();
        assertThat(brackets.find(karimsAds.id()).orElseThrow().closeKind())
                .describedAs("R8.2 - Karim finished a different bracket; the ads are simply next")
                .isEmpty();
        assertThat(brackets.find(noursVideo.id()).orElseThrow().closeKind())
                .describedAs("R8.2 - Nour declared a wait elsewhere; declaring one is working")
                .isEmpty();

        assertThat(brackets.find(mariasInvoices.id()).orElseThrow().closeKind())
                .describedAs("R8 - Maria has not appeared anywhere since her nudge; the window is the backstop")
                .contains(CloseKind.LAPSED);
    }

    @Test
    @DisplayName("R8.1 — six weeks of weekly activity, six sweeps, one nudge, and the bracket lives by right")
    void aBracketWorkedOnEveryWeekLivesIndefinitely() {
        JobId job = job("Aurora Coffee · autumn campaign", maria, "OPEN", false);
        UUID karim = person("karim");

        Instant nudgedAt = Instant.now().minus(Duration.ofDays(42));

        WorkBracket karimsVideo = openBracket(job, "VIDEO", karim, nudgedAt.minus(Duration.ofDays(7)));
        karimsVideo.nudged(nudgedAt);
        brackets.save(karimsVideo);

        WorkBracket mariasInvoices = nudgedAndUntouchedSince(job, "FINANCE", maria, nudgedAt);

        Instant theOneNudge =
                brackets.find(karimsVideo.id()).orElseThrow().nudgedAt().orElseThrow();

        for (int week = 1; week <= 6; week++) {
            WorkBracket stillGoing = brackets.find(karimsVideo.id()).orElseThrow();
            stillGoing.touched(nudgedAt.plus(Duration.ofDays(7L * week)));
            brackets.save(stillGoing);

            rhythm.nudgeTheIdle(WINDOWS);
            rhythm.lapseTheSilent(WINDOWS);
        }

        WorkBracket video = brackets.find(karimsVideo.id()).orElseThrow();

        assertThat(video.state())
                .describedAs("R8.1 - he worked on it every one of those weeks; there is nothing here to end")
                .isEqualTo(BracketState.OPEN);
        assertThat(video.closeKind())
                .describedAs("six sweeps found the same live work six times and wrote no ending")
                .isEmpty();
        assertThat(video.nudgedAt())
                .describedAs("R8 - one nudge for all time, and six weeks of silence from the zone afterwards")
                .contains(theOneNudge);

        assertThat(brackets.find(mariasInvoices.id()).orElseThrow().closeKind())
                .describedAs("and the sweep was working the whole time - Maria, who stopped, lapsed")
                .contains(CloseKind.LAPSED);
    }

    @Test
    @DisplayName("DISCOVERY_23 §3 — the idle and lapse windows are five days; the other two are unchanged")
    void theIdleAndLapseWindowsAreFiveDays() {
        RhythmWindows defaults = RhythmWindows.defaults();

        assertThat(defaults.idleBeforeNudge())
                .describedAs("DISCOVERY_23 §3 - three days nudged people who were merely queueing")
                .isEqualTo(Duration.ofDays(5));
        assertThat(defaults.lapseAfterNudge())
                .describedAs("DISCOVERY_23 §3 - the backstop for genuine absence, not for a busy week")
                .isEqualTo(Duration.ofDays(5));
        assertThat(defaults.longExternalWait())
                .describedAs("DISCOVERY_23 §3 - N6 is unchanged")
                .isEqualTo(Duration.ofDays(7));
        assertThat(defaults.standingCadence())
                .describedAs("R6.2 - the retainer's month is unchanged")
                .isEqualTo(Duration.ofDays(30));
    }

    @Test
    @DisplayName("R8 — a bracket gets exactly one nudge ever, and the second attempt is refused")
    void oneNudgeAndNoSecond() {
        JobId job = job("Aurora Coffee · supplier chase", maria, "OPEN", false);
        WorkBracket chase = openBracket(job, "PROCUREMENT", maria, Instant.now().minus(Duration.ofDays(2)));

        rhythm.nudgeTheIdle(WINDOWS);

        Instant firstNudge = brackets.find(chase.id())
                .orElseThrow()
                .nudgedAt()
                .orElseThrow(() -> new AssertionError("R8 - the idle bracket was never nudged at all"));

        rhythm.nudgeTheIdle(WINDOWS);

        assertThat(brackets.find(chase.id()).orElseThrow().nudgedAt())
                .describedAs("R8 - a second pass finds the first nudge still standing and sends nothing")
                .contains(firstNudge);
        assertThat(jdbc.queryForObject(
                        "select count(*) from work_bracket where id = ? and nudged_at = ?",
                        Integer.class,
                        chase.id().value(),
                        Timestamp.from(firstNudge)))
                .isEqualTo(1);

        assertThatThrownBy(() -> brackets.find(chase.id()).orElseThrow().nudged(Instant.now()))
                .describedAs("R8 - the refusal lives in the aggregate, not only in the sweep's query")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already had its one nudge");
    }

    @Test
    @DisplayName("R6.2 — a retainer's month closes CADENCE_CLOSED, releases nobody, and leaves the boundary open")
    void theCadenceClosesTheMonthAndSatisfiesNoWait() {
        JobId retainer = job("Aurora Coffee · social retainer", maria, "STANDING", true);
        Instant lastSpring = Instant.now().minus(Duration.ofDays(90));

        WorkBracket boundary = boundaryBracket(retainer, lastSpring);
        WorkBracket mariasSocial = openBracket(retainer, "SOCIAL", maria, lastSpring);
        WorkBracket andreisPrint = openBracket(retainer, "PRINT", andrei, Instant.now());

        WorkNodeWait andreiWaits = WorkNodeWait.declared(
                brackets.nextWaitId(),
                andreisPrint,
                WaitKind.COLLEAGUE,
                mariasSocial,
                "need the September posts before the print run",
                null,
                Instant.now());
        brackets.save(andreiWaits);

        rhythm.closeStandingCadences(WINDOWS);

        assertThat(brackets.find(mariasSocial.id()).orElseThrow().closeKind())
                .describedAs("R6.2 - the month rolled over, so the retainer's work becomes evidence at all")
                .contains(CloseKind.CADENCE_CLOSED);

        assertThat(jdbc.queryForObject(
                        "select satisfied_at from work_node_wait where id = ?", Timestamp.class, andreiWaits.id()))
                .describedAs("R7.6/R7.8 - only DELIVERED and DONE satisfy a wait; a cadence close delivered nothing")
                .isNull();
        assertThat(jdbc.queryForObject(
                        "select cancelled_at from work_node_wait where id = ?", Timestamp.class, andreiWaits.id()))
                .describedAs("R7.8 - Andrei is told the thing he awaited died, which is not the same as it arriving")
                .isNotNull();

        assertThat(brackets.find(boundary.id()).orElseThrow().state())
                .describedAs("D8 - closing the container would end the whole engagement every thirty days")
                .isEqualTo(BracketState.OPEN);
        assertThat(brackets.find(andreisPrint.id()).orElseThrow().closeKind())
                .describedAs("R6.2 - work opened this month has not reached its cadence")
                .isEmpty();
    }

    @Test
    @DisplayName("R16.3 — the cadence does not run inside a force-closed engagement")
    void aForceClosedRetainerStopsItsCadence() {
        JobId cancelled = job("Aurora Coffee · retainer, cancelled", maria, "FORCE_CLOSED", true);
        WorkBracket mariasSocial =
                openBracket(cancelled, "SOCIAL", maria, Instant.now().minus(Duration.ofDays(90)));

        rhythm.closeStandingCadences(WINDOWS);

        WorkBracket after = brackets.find(mariasSocial.id()).orElseThrow();
        assertThat(after.closeKind())
                .describedAs("R16.3 - the engagement ended in September; the rhythm has nothing left to close")
                .isEmpty();
        assertThat(after.state())
                .describedAs("the sweep walked past this bracket rather than touching it")
                .isEqualTo(BracketState.OPEN);
    }

    private WorkBracket openBracket(JobId job, String workType, UUID performer, Instant openedAt) {
        BracketAddress address = new BracketAddress(conversation, null, null, workType, performer);
        WorkBracket bracket =
                WorkBracket.opened(brackets.nextBracketId(), job, address, node(job, openedAt), performer, openedAt);
        brackets.save(bracket);
        return bracket;
    }

    private WorkBracket nudgedAndUntouchedSince(JobId job, String workType, UUID holder, Instant nudgedAt) {
        WorkBracket bracket = openBracket(job, workType, holder, nudgedAt.minus(Duration.ofDays(1)));
        bracket.nudged(nudgedAt);
        brackets.save(bracket);
        return bracket;
    }

    private void saidSomethingInTheChannel(UUID author, Instant at) {
        jdbc.update(
                """
                insert into message (id, conversation_id, author_id, body, sent_at, seq, kind)
                values (?, ?, ?, 'next round of proofs is up', ?, 0, 'SPOKEN')
                """,
                UUID.randomUUID(),
                channel(),
                author,
                Timestamp.from(at));
    }

    private UUID channel() {
        jdbc.update(
                """
                insert into workspace (id, name, workspace_use, singleton, created_at)
                values (?, 'Atelier', 'AGENCY', true, ?)
                on conflict (singleton) do nothing
                """,
                UUID.randomUUID(),
                Timestamp.from(Instant.now()));

        UUID workspace = jdbc.queryForObject("select id from workspace limit 1", UUID.class);

        jdbc.update(
                """
                insert into conversation (id, workspace_id, kind, created_at)
                values (?, ?, 'ANNOUNCEMENT', ?)
                on conflict do nothing
                """,
                UUID.randomUUID(),
                workspace,
                Timestamp.from(Instant.now()));

        return jdbc.queryForObject(
                "select id from conversation where workspace_id = ? and kind = 'ANNOUNCEMENT'", UUID.class, workspace);
    }

    private void markedAMessageAsWork(JobId job, UUID marker, Instant at) {
        jdbc.update(
                """
                insert into work_node (id, job_id, text, creator_id, marker_id, created_at,
                                       state, direction, kind)
                values (?, ?, 'proofs going out this afternoon', ?, ?, ?, 'MARKED', 'STANDALONE', 'WORK')
                """,
                UUID.randomUUID(),
                job.value(),
                marker,
                marker,
                Timestamp.from(at));
    }

    private WorkBracket boundaryBracket(JobId job, Instant openedAt) {
        BracketAddress address = new BracketAddress(conversation, null, null, "CLIENT_INTAKE", maria);
        WorkBracket boundary =
                WorkBracket.boundary(brackets.nextBracketId(), job, address, node(job, openedAt), maria, openedAt);
        brackets.save(boundary);
        return boundary;
    }

    private WorkNodeId node(JobId job, Instant at) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into work_node (id, job_id, text, creator_id, created_at, state, direction, kind) "
                        + "values (?, ?, 'a sentence somebody said', ?, ?, 'MARKED', 'STANDALONE', 'WORK')",
                id,
                job.value(),
                maria,
                Timestamp.from(at));
        return WorkNodeId.of(id);
    }

    private UUID person(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into auth_user (id, email, account_state, role_name, created_at) "
                        + "values (?, ?, 'ACTIVE', 'EMPLOYEE', ?)",
                id,
                name + "-" + id + "@atelier.ro",
                Timestamp.from(Instant.now()));
        return id;
    }

    private JobId job(String name, UUID openedBy, String status, boolean standing) {
        UUID id = UUID.randomUUID();
        boolean ended = "FORCE_CLOSED".equals(status);
        Instant now = Instant.now();

        jdbc.update(
                "insert into job (id, name, status, standing, opened_at, opened_by, last_activity_at, "
                        + "closed_at, close_reason, shape_eligible) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                name,
                status,
                standing,
                Timestamp.from(now.minus(Duration.ofDays(120))),
                openedBy,
                Timestamp.from(now),
                ended ? Timestamp.from(now) : null,
                ended ? "the client cancelled the retainer" : null,
                !ended);
        return JobId.of(id);
    }
}
