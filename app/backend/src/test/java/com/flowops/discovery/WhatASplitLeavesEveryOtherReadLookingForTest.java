package com.flowops.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.discovery.application.canvas.CanvasReadPort;
import com.flowops.discovery.application.collaboration.SeeWhoWorkedTogether;
import com.flowops.discovery.application.shared.port.TrackerRailPort;
import com.flowops.discovery.application.shared.port.WorkBracketPort;
import com.flowops.discovery.application.shared.port.WorkGraphPort;
import com.flowops.discovery.domain.enums.Direction;
import com.flowops.discovery.domain.enums.EvidenceOrigin;
import com.flowops.discovery.domain.enums.NodeKind;
import com.flowops.discovery.domain.model.BracketAddress;
import com.flowops.discovery.domain.model.BracketId;
import com.flowops.discovery.domain.model.CollaborationTier;
import com.flowops.discovery.domain.model.JobId;
import com.flowops.discovery.domain.model.TrackId;
import com.flowops.discovery.domain.model.WorkBracket;
import com.flowops.discovery.domain.model.WorkNode;
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

class WhatASplitLeavesEveryOtherReadLookingForTest extends ApplicationTest {
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private WorkGraphPort graph;

    @Autowired
    private WorkBracketPort brackets;

    @Autowired
    private CanvasReadPort canvas;

    @Autowired
    private TrackerRailPort rail;

    @Autowired
    private SeeWhoWorkedTogether collaboration;

    private static final Instant NOW = Instant.parse("2026-08-26T09:00:00Z");

    private UUID lena;
    private UUID sara;
    private UUID karim;
    private JobId job;
    private UUID channel;

    @BeforeEach
    void anAssignmentMessageAndTheTwoPeopleItNames() {
        lena = person("lena");
        sara = person("sara");
        karim = person("karim");
        job = JobId.of(job(lena));
        channel = conversationBetween(sara, karim);
    }

    @Test
    @DisplayName("DISCOVERY_24 M3–M7 — a second mark rewrites the first, so every row on the sentence says SPLIT")
    void aSecondMarkOnOneSentenceTurnsEveryRowIntoASplit() {
        UUID assignment = message(NOW);
        WorkNode saras = marked("captions for the six posts", sara);
        WorkNode karims = marked("the designs", karim);

        graph.recordEvidence(saras, assignment, EvidenceOrigin.PRIMARY);
        graph.recordEvidence(karims, assignment, EvidenceOrigin.PRIMARY);

        assertThat(originsOn(assignment))
                .describedAs("reading either row alone has to say it was a fan-out, so both are rewritten")
                .containsExactly("SPLIT", "SPLIT");
        assertThat(jdbc.queryForObject(
                        "select count(*) from work_node_evidence where message_id = ?", Integer.class, assignment))
                .describedAs("R12.7 - the earlier row is rewritten, not replaced; nothing is deleted")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("a sentence only one person marked stays PRIMARY")
    void oneMarkOnOneSentenceIsNotASplit() {
        UUID sentence = message(NOW);
        graph.recordEvidence(marked("captions for the six posts", sara), sentence, EvidenceOrigin.PRIMARY);

        assertThat(originsOn(sentence)).containsExactly("PRIMARY");
    }

    @Test
    @DisplayName("the third mark on one sentence joins the split rather than starting a new PRIMARY")
    void athirdMarkStaysInsideTheSplit() {
        UUID assignment = message(NOW);
        graph.recordEvidence(marked("captions", sara), assignment, EvidenceOrigin.PRIMARY);
        graph.recordEvidence(marked("designs", karim), assignment, EvidenceOrigin.PRIMARY);
        graph.recordEvidence(marked("the video", lena), assignment, EvidenceOrigin.PRIMARY);

        assertThat(originsOn(assignment)).containsExactly("SPLIT", "SPLIT", "SPLIT");
    }

    @Test
    @DisplayName("the clock starts at the marked sentence even after its row was rewritten to SPLIT")
    void theStartOfTheWorkIsStillTheSentenceThatWasMarked() {
        UUID marked = message(NOW);
        UUID attachedAfterwards = message(NOW.minusSeconds(3600));
        WorkNode node = marked("the designs", karim);

        evidence(node.id().value(), marked, "SPLIT");
        evidence(node.id().value(), attachedAfterwards, "ADDITIONAL");

        assertThat(graph.evidenceMessageSentAt(node.id()))
                .describedAs("context attached later was said earlier, and it does not move the start of "
                        + "the work backwards")
                .contains(NOW);
    }

    @Test
    @DisplayName("R4.3 — a bracket opened from a fanned-out sentence still closes onto that sentence")
    void thePairedEndStillStandsOnTheSameSentence() {
        UUID assignment = message(NOW);
        WorkNode start = marked("the designs", karim);
        evidence(start.id().value(), assignment, "SPLIT");

        BracketId id = brackets.nextBracketId();
        brackets.save(WorkBracket.opened(
                id, job, new BracketAddress(channel, null, null, "DESIGN", karim), start.id(), karim, NOW));

        WorkNodeId end = brackets.appendEndNode(brackets.find(id).orElseThrow(), karim, NOW.plusSeconds(7200));

        assertThat(jdbc.queryForList(
                        "select message_id from work_node_evidence where work_node_id = ?", UUID.class, end.value()))
                .describedAs("R4.3 - the paired END is generated from the opening message, and a node "
                        + "carrying no evidence row is work nothing can explain afterwards")
                .containsExactly(assignment);
    }

    @Test
    @DisplayName("a card built from a fanned-out sentence still knows which message to open")
    void theCanvasCardStillOpensItsSentence() {
        UUID assignment = message(NOW);
        UUID thread = track(karim);
        WorkNode node = marked("the designs", karim);
        node.placedIn(TrackId.of(thread));
        graph.save(node);
        evidence(node.id().value(), assignment, "SPLIT");

        List<CanvasReadPort.CardRow> cards = canvas.cardsOf(job.value());

        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).messageId())
                .describedAs("the card is drawn either way; what a wrong origin filter removes is the way back")
                .isEqualTo(assignment);
        assertThat(cards.get(0).conversationId()).isEqualTo(channel);
    }

    @Test
    @DisplayName("a rail mark from a fanned-out sentence still knows which message to open")
    void theRailStillOpensItsSentence() {
        UUID assignment = message(NOW);
        WorkNode start = marked("the designs", karim);
        evidence(start.id().value(), assignment, "SPLIT");
        bracket(start.id().value(), "DESIGN", karim);

        TrackerRailPort.Lane lane = rail.openWork().stream()
                .filter(row -> row.jobId().equals(job.value()))
                .findFirst()
                .orElseThrow();

        assertThat(lane.marks()).hasSize(1);
        assertThat(lane.marks().get(0).messageId()).isEqualTo(assignment);
    }

    @Test
    @DisplayName("R19.2 — two people asked for one thing in one sentence still collapse to one step")
    void twoPeopleAskedForOneThingAreStillOneStep() {
        UUID assignment = message(NOW);

        WorkNode sarasStart = marked("the video", sara);
        evidence(sarasStart.id().value(), assignment, "SPLIT");
        bracket(sarasStart.id().value(), "VIDEO", sara);

        WorkNode karimsStart = marked("the video", karim);
        evidence(karimsStart.id().value(), assignment, "SPLIT");
        bracket(karimsStart.id().value(), "VIDEO", karim);

        List<SeeWhoWorkedTogether.Group> groups = collaboration.inJob(job);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).tier())
                .describedAs("R19.2 - one message and one work type is good evidence; falling to WEAK puts "
                        + "a step in the learned process that two people performed as one")
                .isEqualTo(CollaborationTier.GOOD);
        assertThat(groups.get(0).collapsesToOneStep()).isTrue();
    }

    private List<String> originsOn(UUID messageId) {
        return jdbc.queryForList(
                "select origin from work_node_evidence where message_id = ? order by added_at, id",
                String.class,
                messageId);
    }

    private void evidence(UUID nodeId, UUID messageId, String origin) {
        jdbc.update(
                "insert into work_node_evidence (id, work_node_id, message_id, added_at, origin) "
                        + "values (?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                nodeId,
                messageId,
                Timestamp.from(NOW),
                origin);
    }

    private WorkNode marked(String text, UUID performer) {
        WorkNode node = WorkNode.marked(
                WorkNodeId.of(UUID.randomUUID()), job, text, performer, null, NodeKind.WORK, Direction.STANDALONE, NOW);
        graph.save(node);
        jdbc.update(
                "update work_node set performer_id = ?, conversation_id = ?, node_role = 'START' where id = ?",
                performer,
                channel,
                node.id().value());
        return node;
    }

    private UUID bracket(UUID openedByNode, String workType, UUID performer) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                insert into work_bracket (id, job_id, conversation_id, work_type, performer_ref,
                    opened_by_node, closure_right, state, is_boundary, opened_at, last_activity_at)
                values (?, ?, ?, ?, ?, ?, ?, 'OPEN', false, ?, ?)
                """,
                id,
                job.value(),
                channel,
                workType,
                performer,
                openedByNode,
                performer,
                Timestamp.from(NOW),
                Timestamp.from(NOW));
        jdbc.update("update work_node set bracket_id = ? where id = ?", id, openedByNode);
        return id;
    }

    private UUID track(UUID performer) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                insert into track (id, job_id, performer_id, solo, key_basis, state, opened_at,
                    last_activity_at)
                values (?, ?, ?, true, 'PERFORMER', 'OPEN', ?, ?)
                """,
                id,
                job.value(),
                performer,
                Timestamp.from(NOW),
                Timestamp.from(NOW));
        return id;
    }

    private UUID message(Instant sentAt) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                insert into message (id, conversation_id, author_id, body, sent_at, seq, kind)
                values (?, ?, ?, 'Sara - captions for the six posts. Karim - the designs.', ?, 0, 'SPOKEN')
                """,
                id,
                channel,
                lena,
                Timestamp.from(sentAt));
        return id;
    }

    private UUID conversationBetween(UUID one, UUID other) {
        jdbc.update(
                """
                insert into workspace (id, name, workspace_use, singleton, created_at)
                values (?, 'Atelier', 'AGENCY', true, ?)
                on conflict (singleton) do nothing
                """,
                UUID.randomUUID(),
                Timestamp.from(NOW));

        UUID workspace = jdbc.queryForObject("select id from workspace limit 1", UUID.class);

        boolean oneIsLower = one.toString().compareTo(other.toString()) < 0;
        UUID lo = oneIsLower ? one : other;
        UUID hi = oneIsLower ? other : one;

        UUID conversation = UUID.randomUUID();
        jdbc.update(
                """
                insert into conversation (id, workspace_id, kind, created_at, participant_lo, participant_hi)
                values (?, ?, 'DIRECT', ?, ?, ?)
                """,
                conversation,
                workspace,
                Timestamp.from(NOW),
                lo,
                hi);

        jdbc.update(
                "insert into conversation_participant (conversation_id, person_id) values (?, ?), (?, ?)",
                conversation,
                one,
                conversation,
                other);

        return conversation;
    }

    private UUID person(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into auth_user (id, email, display_name, account_state, role_name, created_at) "
                        + "values (?, ?, ?, 'ACTIVE', 'EMPLOYEE', ?)",
                id,
                name + "-" + id + "@atelier.ro",
                name,
                Timestamp.from(NOW));
        return id;
    }

    private UUID job(UUID openedBy) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into job (id, name, status, standing, opened_at, opened_by, last_activity_at) "
                        + "values (?, 'Aurora Coffee · summer menu', 'OPEN', false, ?, ?, ?)",
                id,
                Timestamp.from(NOW),
                openedBy,
                Timestamp.from(NOW));
        return id;
    }
}
