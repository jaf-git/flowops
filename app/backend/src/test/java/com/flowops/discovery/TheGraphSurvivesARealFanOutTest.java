package com.flowops.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.discovery.application.publicgraph.ViewPublicGraph;
import com.flowops.discovery.application.shared.port.PublicGraphPort;
import com.flowops.discovery.domain.model.JobId;
import com.flowops.support.ApplicationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class TheGraphSurvivesARealFanOutTest extends ApplicationTest {
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ViewPublicGraph graph;

    private static final Instant NOW = Instant.parse("2026-08-26T09:00:00Z");

    private static final int FAN_OUT = 18;

    private UUID owner;
    private UUID job;
    private UUID conversation;
    private UUID boundaryNode;

    private static final List<String> WORK_TYPES = List.of(
            "CONTENT",
            "PHOTO",
            "DESIGN",
            "VIDEO",
            "ADS",
            "COPY",
            "RESEARCH",
            "SCHEDULING",
            "REPORTING",
            "CLIENT_INTAKE",
            "PROOFREAD",
            "ILLUSTRATION",
            "ANIMATION",
            "SOUND",
            "TRANSLATION",
            "PRINT",
            "BUDGET",
            "REVIEW");

    @BeforeEach
    void anEngagementWithEighteenPiecesOfWork() {
        owner = person("owner-" + UUID.randomUUID() + "@atelier.ro");
        job = job("Aurora Coffee · Ramadan launch", owner);
        conversation = UUID.randomUUID();
        boundaryNode = node(job, owner, null, "JOB_START");
        bracket(owner, "CLIENT_INTAKE_BOUNDARY", "OPEN", true, boundaryNode);
    }

    @Test
    @DisplayName("every one of eighteen brackets reaches the graph — none is lost to the join")
    void noBracketGoesMissingAtDensity() {
        List<UUID> brackets = eighteenSiblings();

        ViewPublicGraph.Graph drawn = graph.graphOf(JobId.of(job));

        Set<UUID> reached = new HashSet<>();
        for (PublicGraphPort.PublicNode node : drawn.nodes()) {
            reached.add(node.bracketId());
        }

        assertThat(brackets).hasSize(FAN_OUT);
        assertThat(reached)
                .as("every bracket in the engagement, boundary included")
                .hasSize(FAN_OUT + 1);
        assertThat(reached).containsAll(brackets);
    }

    @Test
    @DisplayName("R18 — eighteen siblings draw as eighteen siblings, not as a chain")
    void aFanOutIsNotAChain() {
        eighteenSiblings();

        ViewPublicGraph.Graph drawn = graph.graphOf(JobId.of(job));

        List<PublicGraphPort.PublicEdge> parentage = drawn.edges().stream()
                .filter(edge -> edge.kind() == PublicGraphPort.PublicEdge.EdgeKind.PARENTAGE)
                .toList();

        assertThat(parentage).as("one edge per sibling, each from the boundary").hasSize(FAN_OUT);

        assertThat(parentage).allSatisfy(edge -> assertThat(edge.fromNodeId()).isEqualTo(boundaryNode));

        Map<UUID, Integer> arrivals = new HashMap<>();
        for (PublicGraphPort.PublicEdge edge : parentage) {
            arrivals.merge(edge.toNodeId(), 1, Integer::sum);
        }
        assertThat(arrivals).hasSize(FAN_OUT);
        assertThat(arrivals.values()).allSatisfy(count -> assertThat(count).isEqualTo(1));
    }

    @Test
    @DisplayName("a graph of eighteen is not silently edgeless")
    void densityDoesNotArriveWithoutItsEdges() {
        eighteenSiblings();

        assertThat(graph.graphOf(JobId.of(job)).edges())
                .as("eighteen pieces of work that came from one brief are related, and the graph says so")
                .isNotEmpty();
    }

    @Test
    @DisplayName("every one of eighteen open brackets still has somebody who can close it")
    void everyOpenBracketCanBeClosedAtDensity() {
        eighteenSiblings();

        Integer orphaned = jdbc.queryForObject(
                """
                select count(*) from work_bracket
                where job_id = ? and state in ('OPEN', 'WAITING') and closure_right is null
                """,
                Integer.class,
                job);

        assertThat(orphaned).isZero();
    }

    @Test
    @DisplayName("closing one of eighteen leaves the other seventeen alone")
    void oneEndingDoesNotEndItsSiblings() {
        List<UUID> brackets = eighteenSiblings();
        UUID ending = brackets.get(0);

        jdbc.update(
                """
                update work_bracket
                set state = 'CLOSED', close_kind = 'DELIVERED', closed_at = ?,
                    output_kind = 'LINK', output_value = 'https://atelier.ro/aurora/copy'
                where id = ?
                """,
                Timestamp.from(NOW),
                ending);

        Integer stillLive = jdbc.queryForObject(
                "select count(*) from work_bracket where job_id = ? and is_boundary = false "
                        + "and state in ('OPEN', 'WAITING')",
                Integer.class,
                job);

        assertThat(stillLive).isEqualTo(FAN_OUT - 1);
    }

    private List<UUID> eighteenSiblings() {
        List<UUID> brackets = new ArrayList<>();

        for (String workType : WORK_TYPES) {
            UUID performer = person(workType.toLowerCase() + "-" + UUID.randomUUID() + "@atelier.ro");
            UUID work = node(job, performer, boundaryNode, "WORK");
            brackets.add(bracket(performer, workType, "OPEN", false, work));
            jdbc.update("update work_node set bracket_id = ? where id = ?", brackets.getLast(), work);
        }

        return brackets;
    }

    private UUID person(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into auth_user (id, email, account_state, role_name, created_at) "
                        + "values (?, ?, 'ACTIVE', 'EMPLOYEE', ?)",
                id,
                email,
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

    private UUID node(UUID jobId, UUID creator, UUID parent, String kind) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                insert into work_node (id, job_id, text, creator_id, created_at, state, direction, kind,
                                       parent_node_id)
                values (?, ?, 'the brief', ?, ?, 'MARKED', 'STANDALONE', ?, ?)
                """,
                id,
                jobId,
                creator,
                Timestamp.from(NOW),
                kind,
                parent);
        return id;
    }

    private UUID bracket(UUID performer, String workType, String state, boolean isBoundary, UUID openedBy) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                insert into work_bracket (
                    id, job_id, conversation_id, work_type, performer_ref, opened_by_node,
                    closure_right, state, is_boundary, opened_at, last_activity_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                job,
                conversation,
                workType,
                performer,
                openedBy,
                performer,
                state,
                isBoundary,
                Timestamp.from(NOW),
                Timestamp.from(NOW));

        if (isBoundary) {
            jdbc.update("update work_node set bracket_id = ? where id = ?", id, openedBy);
        }

        return id;
    }
}
