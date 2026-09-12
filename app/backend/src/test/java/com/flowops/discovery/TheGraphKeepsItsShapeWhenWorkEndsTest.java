package com.flowops.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowops.discovery.application.closebracket.CloseBracket;
import com.flowops.discovery.application.closejob.CloseJob;
import com.flowops.discovery.application.closejob.JobStillHasLiveWorkException;
import com.flowops.discovery.application.markintobracket.PlaceMarkInBracket;
import com.flowops.discovery.application.shared.port.ConversationWorkPort;
import com.flowops.discovery.application.shared.port.ConversationWorkPort.ConversationBracket;
import com.flowops.discovery.application.shared.port.JobHeaderPort;
import com.flowops.discovery.application.shared.port.WorkBracketPort;
import com.flowops.discovery.domain.enums.CloseKind;
import com.flowops.discovery.domain.enums.OutputKind;
import com.flowops.discovery.domain.model.BracketAddress;
import com.flowops.discovery.domain.model.Job;
import com.flowops.discovery.domain.model.JobId;
import com.flowops.discovery.domain.model.MarkOutcome;
import com.flowops.discovery.domain.model.WorkBracket;
import com.flowops.discovery.domain.model.WorkNodeId;
import com.flowops.support.ApplicationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class TheGraphKeepsItsShapeWhenWorkEndsTest extends ApplicationTest {
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlaceMarkInBracket marking;

    @Autowired
    private JobHeaderPort headers;

    @Autowired
    private CloseBracket closing;

    @Autowired
    private CloseJob jobs;

    @Autowired
    private WorkBracketPort brackets;

    @Autowired
    private ConversationWorkPort conversationWork;

    private static final Instant NOW = Instant.parse("2026-08-26T09:00:00Z");

    private UUID omar;
    private UUID karim;
    private JobId job;
    private UUID conversation;

    @BeforeEach
    void anEngagementInTheTeamChannel() {
        omar = person("omar");
        karim = person("karim");
        conversation = UUID.randomUUID();
        job = JobId.of(job("Sunrise Bakery · summer menu", omar));
    }

    @Test
    @DisplayName("R18 — a dense day of work: closing the first bracket ends nothing else")
    void closingOneBracketEndsNoOther() {
        List<WorkBracket> omarsDay = List.of(
                open("CLIENT_INTAKE", omar),
                open("COORDINATION", omar),
                open("REPORTING", omar),
                open("FINANCE", omar),
                open("SCHEDULING", omar));

        closing.done(omarsDay.get(0).id(), omar);

        for (WorkBracket later : omarsDay.subList(1, omarsDay.size())) {
            WorkBracket after = brackets.find(later.id()).orElseThrow();

            assertThat(after.state().isTerminal())
                    .describedAs(
                            "R18.2 - force-closure happens at job level only, never between bracket "
                                    + "pairs; bracket %s was ended by somebody else finishing their own work",
                            later.id().value())
                    .isFalse();
        }

        assertThat(jdbc.queryForObject(
                        "select count(*) from work_bracket where job_id = ? and close_kind = 'PARENT_CLOSED'",
                        Integer.class,
                        job.value()))
                .describedAs("nothing in an ordinary close may produce a PARENT_CLOSED bracket")
                .isZero();
    }

    @Test
    @DisplayName("R4a.1 — a mark matching the boundary's address opens beside it, never into it")
    void realWorkNeverJoinsTheContainer() {
        BracketAddress address = new BracketAddress(conversation, null, null, "CLIENT_INTAKE", omar);
        WorkBracket boundary = WorkBracket.boundary(brackets.nextBracketId(), job, address, node(), omar, NOW);
        brackets.save(boundary);

        MarkOutcome outcome = marking.place(job, address, node(), omar);

        assertThat(outcome)
                .describedAs("R4a.1 - the boundary is never joinable")
                .isInstanceOf(MarkOutcome.Opened.class);
        assertThat(outcome.bracket().id()).isNotEqualTo(boundary.id());
        assertThat(outcome.bracket().isBoundary()).isFalse();

        assertThat(jdbc.queryForObject(
                        "select count(*) from work_node where bracket_id = ?",
                        Integer.class,
                        boundary.id().value()))
                .describedAs("the boundary holds zero work nodes for the life of the job")
                .isZero();
    }

    @Test
    @DisplayName("R4a.1 — the Work tab, the analytics tile and the engagement bar count the same live work")
    void everySurfaceGivesOneAnswer() {
        BracketAddress boundaryAddress = new BracketAddress(conversation, null, null, "CLIENT_INTAKE", omar);
        WorkBracket boundary = WorkBracket.boundary(brackets.nextBracketId(), job, boundaryAddress, node(), omar, NOW);
        brackets.save(boundary);

        WorkBracket sunriseDesign = open("DESIGN", karim);
        open("PHOTO", karim);
        open("COPY", omar);
        closing.done(sunriseDesign.id(), karim);

        List<ConversationBracket> workTab = conversationWork.workIn(conversation);

        assertThat(workTab)
                .extracting(ConversationBracket::bracketId)
                .describedAs("R4a.1 - the container is not a row of work, and a tab that lists it invites "
                        + "somebody to close, hand over or declare a wait on the engagement itself")
                .doesNotContain(boundary.id().value());

        long badge = workTab.stream().filter(ConversationBracket::isLive).count();
        long ended = workTab.stream().filter(row -> row.closeKind() != null).count();
        long stillLive = workTab.size() - ended;
        int bar = headers.headerOf(job).orElseThrow().liveBrackets();

        assertThat(badge)
                .describedAs("the Work tab's badge - Karim's photos and Omar's copy, and nothing else")
                .isEqualTo(2);
        assertThat(stillLive)
                .describedAs("the analytics tile's *N still live*, which is this same read minus the endings")
                .isEqualTo(badge);
        assertThat((long) bar)
                .describedAs("R4a.1 - the engagement bar already excluded the boundary; the other two now agree "
                        + "with it rather than it being the odd one out")
                .isEqualTo(badge);
    }

    @Test
    @DisplayName("R4a.1 — hiding the boundary from the Work tab leaves the boundary itself untouched")
    void theBoundaryIsHiddenRatherThanRemoved() {
        BracketAddress boundaryAddress = new BracketAddress(conversation, null, null, "CLIENT_INTAKE", omar);
        WorkBracket boundary = WorkBracket.boundary(brackets.nextBracketId(), job, boundaryAddress, node(), omar, NOW);
        brackets.save(boundary);

        conversationWork.workIn(conversation);

        WorkBracket after = brackets.find(boundary.id()).orElseThrow();
        assertThat(after.isBoundary()).isTrue();
        assertThat(after.state().isTerminal())
                .describedAs("R15.3 - the container stays open until the engagement ends, and its ending is "
                        + "the only thing that makes a shape")
                .isFalse();
    }

    @Test
    @DisplayName("R4.3 — one-message work closes with a paired END from its own message, not a mutated START")
    void oneMessageWorkGetsAPairedEnd() {
        WorkBracket bracket = open("PHOTO", karim);
        UUID startNode = bracket.openedByNode().value();

        closing.delivered(bracket.id(), OutputKind.LINK, "https://drive.example/photos", karim);

        var end = jdbc.queryForMap(
                "select id, node_role, evidence_origin, parent_node_id from work_node "
                        + "where bracket_id = ? and node_role = 'END'",
                bracket.id().value());

        assertThat(end.get("evidence_origin"))
                .describedAs("R4.3 - a bracket holding nothing but its START is one-message work")
                .isEqualTo("SELF_CLOSE");
        assertThat(end.get("parent_node_id"))
                .describedAs("R4.7 - the END's parent is the last node in the chain, here the START")
                .isEqualTo(startNode);
        assertThat(end.get("id"))
                .describedAs("R4.2 - a START never becomes an END; the paired end is a new node")
                .isNotEqualTo(startNode);

        assertThat(jdbc.queryForObject("select node_role from work_node where id = ?", String.class, startNode))
                .describedAs("the START is left exactly as it was")
                .isEqualTo("START");
    }

    @Test
    @DisplayName("R15.2 — an engagement with live work refuses to close, and says how much")
    void aJobWithLiveWorkRefusesToClose() {
        open("DESIGN", karim);

        assertThatThrownBy(() -> jobs.close(job, omar))
                .isInstanceOf(JobStillHasLiveWorkException.class)
                .hasMessageContaining("still has 1 live bracket");
    }

    @Test
    @DisplayName("R15.3 — closing the last bracket makes the job ready, and closing it ends the boundary")
    void theBoundaryFinallyGetsItsEnd() {
        BracketAddress boundaryAddress = new BracketAddress(conversation, null, null, "CLIENT_INTAKE", omar);
        WorkBracket boundary = WorkBracket.boundary(brackets.nextBracketId(), job, boundaryAddress, node(), omar, NOW);
        brackets.save(boundary);

        WorkBracket work = open("DESIGN", karim);
        closing.done(work.id(), karim);

        assertThat(jobs.reconsider(job).ready())
                .describedAs("R15.1 - the boundary does not count against itself")
                .isTrue();

        var ended = jobs.close(job, omar);

        WorkBracket closedBoundary = brackets.find(boundary.id()).orElseThrow();
        assertThat(closedBoundary.closeKind()).contains(CloseKind.JOB_END);
        assertThat(closedBoundary.closedByNode()).isPresent();

        UUID openingNode = closedBoundary.openedByNode().value();
        UUID endingNode = closedBoundary.closedByNode().orElseThrow().value();

        assertThat(jdbc.queryForObject("select paired_node_id from work_node where id = ?", UUID.class, openingNode))
                .describedAs("R15.10 - the JOB_START names the JOB_END back")
                .isEqualTo(endingNode);
        assertThat(jdbc.queryForObject("select paired_node_id from work_node where id = ?", UUID.class, endingNode))
                .describedAs("R15.10 - and the JOB_END names the JOB_START")
                .isEqualTo(openingNode);

        assertThat(ended.shapeEligible())
                .describedAs("R15.5 - every bracket ended in a known outcome, so this job is evidence")
                .isTrue();
    }

    @Test
    @DisplayName("R15.10 — new work in a ready engagement reopens it rather than forcing a rework job")
    void aReadyEngagementReopensWhenLateWorkArrives() {
        WorkBracket design = open("DESIGN", karim);
        closing.done(design.id(), karim);

        assertThat(jobs.reconsider(job).status())
                .describedAs("nothing live, so it is ready")
                .isEqualTo(Job.Status.READY_TO_CLOSE);

        open("DESIGN", karim);

        assertThat(jobs.reconsider(job).status())
                .describedAs("R15.10 - and the late message brings it back rather than starting a second job")
                .isEqualTo(Job.Status.OPEN);
    }

    @Test
    @DisplayName("R16 — a forced ending scars the job, tells the people working, and teaches nothing")
    void aForcedEndingIsVisiblyDifferent() {
        BracketAddress boundaryAddress = new BracketAddress(conversation, null, null, "CLIENT_INTAKE", omar);
        brackets.save(WorkBracket.boundary(brackets.nextBracketId(), job, boundaryAddress, node(), omar, NOW));

        WorkBracket karimsDesign = open("DESIGN", karim);

        var ended = jobs.forceClose(job, omar, "client pulled the budget");

        assertThat(ended.tellThem())
                .describedAs("R16.5 - everybody holding live work is told once")
                .containsExactly(karim);
        assertThat(ended.shapeEligible())
                .describedAs("D12 - the graph is truncated, so the shape it would teach never happened")
                .isFalse();

        WorkBracket cut = brackets.find(karimsDesign.id()).orElseThrow();
        assertThat(cut.closeKind()).contains(CloseKind.PARENT_CLOSED);
        assertThat(cut.countsAsPatternEvidence())
                .describedAs("R15.5 - nobody ever learned how this work would have turned out")
                .isFalse();

        assertThat(jdbc.queryForObject("select close_reason from job where id = ?", String.class, job.value()))
                .isEqualTo("client pulled the budget");
    }

    @Test
    @DisplayName("R16 — the header calls a forced ending CLOSED, and the reason is what says it was forced")
    void theHeaderFoldsAForcedEndingIntoClosed() {
        BracketAddress boundaryAddress = new BracketAddress(conversation, null, null, "CLIENT_INTAKE", omar);
        brackets.save(WorkBracket.boundary(brackets.nextBracketId(), job, boundaryAddress, node(), omar, NOW));
        open("DESIGN", karim);

        jobs.forceClose(job, omar, "client pulled the budget");

        var header = headers.headerOf(job).orElseThrow();

        assertThat(header.status())
                .describedAs("R16 - a forced ending is a CLOSED engagement, never a sixth status the bar cannot read")
                .isEqualTo("CLOSED");
        assertThat(header.closeReason())
                .describedAs("R16 - and the reason is the whole of what makes it forced rather than finished")
                .isEqualTo("client pulled the budget");
        assertThat(header.shapeEligible())
                .describedAs("R15.5 - the bar shows *not part of the learned shape* only once the job reads closed")
                .isFalse();
        assertThat(jdbc.queryForObject("select status from job where id = ?", String.class, job.value()))
                .describedAs("the record itself keeps the distinction; only the header folds it")
                .isEqualTo("FORCE_CLOSED");
    }

    @Test
    @DisplayName("R5.3 — assigning work carries the closure right with it")
    void namingAPerformerTransfersTheObligation() {
        BracketAddress karimsWork = new BracketAddress(conversation, null, null, "DESIGN", karim);

        MarkOutcome outcome = marking.place(job, karimsWork, node(), omar);

        assertThat(outcome.bracket().closureRight())
                .describedAs("R5.3 - assigning work is the opt-in that carries the obligation")
                .isEqualTo(karim);
    }

    private WorkBracket open(String workType, UUID performer) {
        BracketAddress address = new BracketAddress(conversation, null, null, workType, performer);
        return marking.place(job, address, node(), performer).bracket();
    }

    private WorkNodeId node() {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into work_node (id, job_id, text, creator_id, created_at, state, direction, kind) "
                        + "values (?, ?, 'a sentence somebody said', ?, ?, 'MARKED', 'STANDALONE', 'WORK')",
                id,
                job.value(),
                omar,
                Timestamp.from(NOW));
        return WorkNodeId.of(id);
    }

    private UUID person(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into auth_user (id, email, account_state, role_name, created_at) "
                        + "values (?, ?, 'ACTIVE', 'EMPLOYEE', ?)",
                id,
                name + "-" + id + "@atelier.ro",
                Timestamp.from(NOW));
        return id;
    }

    private UUID job(String name, UUID openedBy) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into job (id, name, status, standing, opened_at, opened_by, last_activity_at) "
                        + "values (?, ?, 'OPEN', false, ?, ?, ?)",
                id,
                name,
                Timestamp.from(NOW),
                openedBy,
                Timestamp.from(NOW));
        return id;
    }
}
