package com.flowops.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.discovery.application.absence.ClosureRightsFollowPeople;
import com.flowops.discovery.application.handover.HandOverBracket;
import com.flowops.discovery.domain.model.BracketId;
import com.flowops.discovery.domain.model.WorkNodeId;
import com.flowops.support.ApplicationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class WhatHappensToWorkWhenSomebodyLeavesAndReturnsTest extends ApplicationTest {
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ClosureRightsFollowPeople reconciliation;

    @Autowired
    private HandOverBracket handovers;

    private static final Instant NOW = Instant.parse("2026-08-26T09:00:00Z");

    private UUID lena;
    private UUID nour;
    private UUID karim;
    private UUID job;
    private UUID conversation;
    private UUID rootNode;

    @BeforeEach
    void anAgencyWithAReportingLine() {
        lena = person("lena-" + UUID.randomUUID() + "@atelier.ro");
        nour = person("nour-" + UUID.randomUUID() + "@atelier.ro");
        karim = person("karim-" + UUID.randomUUID() + "@atelier.ro");

        UUID workspace = workspace();
        UUID lenasMembership = membership(workspace, lena, rootOf(workspace));
        membership(workspace, nour, lenasMembership);
        membership(workspace, karim, lenasMembership);

        job = job("Sunrise Bakery · summer menu", lena);
        conversation = UUID.randomUUID();
        rootNode = node(job, lena);
    }

    @Test
    @DisplayName("T25 — when Nour goes, her closure right walks up to Lena and remembers it was Nour's")
    void aDepartureNeverLeavesABracketUnclosable() {
        UUID teaser = bracket(nour, "TEASER", "OPEN", nour);

        deactivate(nour);
        reconciliation.sweep();

        assertThat(closureRightOn(teaser)).as("somebody active can end it").isEqualTo(lena);
        assertThat(standsInForOn(teaser)).as("and it is still Nour's work").isEqualTo(nour);
    }

    @Test
    @DisplayName("T27 — Nour returns: the teaser comes home, the handed-over video stays with Karim")
    void areturnBringsBackOnlyWhatWasNeverHandedOver() {
        UUID teaser = bracket(nour, "TEASER", "OPEN", nour);

        UUID handedOver = bracket(nour, "VIDEO", "OPEN", nour);

        jdbc.update(
                "update work_bracket set state = 'CLOSED', close_kind = 'HANDED_OVER', closed_at = ? where id = ?",
                Timestamp.from(NOW),
                handedOver);
        UUID successor = bracket(karim, "VIDEO", "OPEN", karim);
        jdbc.update("update work_bracket set continues_bracket_id = ? where id = ?", handedOver, successor);

        deactivate(nour);
        reconciliation.sweep();
        assertThat(closureRightOn(teaser)).as("she has gone, so Lena holds it").isEqualTo(lena);

        reactivate(nour);
        reconciliation.sweep();

        assertThat(closureRightOn(teaser)).as("R17.1 — her own work comes home").isEqualTo(nour);
        assertThat(standsInForOn(teaser))
                .as("and nobody is standing in any more")
                .isNull();

        assertThat(closureRightOn(successor))
                .as("R17.1 — the video belongs to its successor permanently; a handover is not a loan")
                .isEqualTo(karim);
    }

    @Test
    @DisplayName("a second sweep over the same rows changes nothing")
    void reconciliationConverges() {
        bracket(nour, "TEASER", "OPEN", nour);
        deactivate(nour);

        reconciliation.sweep();
        ClosureRightsFollowPeople.Reconciled again = reconciliation.sweep();

        assertThat(again.changedAnything())
                .as("the second pass over an already-repaired workspace")
                .isFalse();
    }

    @Test
    @DisplayName("R5.1 — the right climbs past a manager who has also left, to the first active person above")
    void aRightIsNeverMovedToSomebodyWhoHasAlsoLeft() {
        UUID teaser = bracket(nour, "TEASER", "OPEN", nour);
        UUID ownerAbove = ownerOfTheLine();

        deactivate(nour);
        deactivate(lena);
        reconciliation.sweep();

        assertThat(closureRightOn(teaser))
                .as("not Lena, who has also gone — the walk continues to somebody who can actually close it")
                .isEqualTo(ownerAbove);
        assertThat(standsInForOn(teaser))
                .as("and through every hop it still remembers the work is Nour's")
                .isEqualTo(nour);
    }

    @Test
    @DisplayName("R14.1 — a handover announced on an already-marked message still gives the successor its own node")
    void aSuccessorAlwaysGetsANodeOfItsOwn() {
        UUID video = bracket(nour, "VIDEO", "OPEN", nour);

        jdbc.update("update work_node set bracket_id = ?, node_role = 'START' where id = ?", video, rootNode);

        HandOverBracket.Handover handed = handovers
                .handOver(BracketId.of(video), karim, WorkNodeId.of(rootNode), true)
                .orElseThrow(() -> new AssertionError("R14.6 - Karim is active, so there is a successor"));

        UUID successor = handed.successor().id().value();

        assertThat(jdbc.queryForObject("select bracket_id from work_node where id = ?", UUID.class, rootNode))
                .describedAs("the announcement stays with the work it opened; moving it empties Nour's bracket")
                .isEqualTo(video);

        var opening = jdbc.queryForList(
                "select id from work_node where bracket_id = ? and node_role = 'START'", UUID.class, successor);

        assertThat(opening)
                .describedAs("#23 - a successor with no node is invisible to every read that joins node to bracket")
                .hasSize(1);

        UUID successorNode = opening.get(0);
        assertThat(jdbc.queryForObject("select opened_by_node from work_bracket where id = ?", UUID.class, successor))
                .describedAs("and the bracket points at its own opening rather than at the predecessor's")
                .isEqualTo(successorNode);
    }

    private UUID ownerOfTheLine() {
        return jdbc.queryForObject(
                "select user_id from workspace_membership where manager_id is null limit 1", UUID.class);
    }

    private UUID closureRightOn(UUID bracket) {
        return jdbc.queryForObject("select closure_right from work_bracket where id = ?", UUID.class, bracket);
    }

    private UUID standsInForOn(UUID bracket) {
        return jdbc.queryForObject("select closure_stands_in_for from work_bracket where id = ?", UUID.class, bracket);
    }

    private void deactivate(UUID who) {
        jdbc.update(
                "update workspace_membership set status = 'DEACTIVATED', deactivated_at = ? where user_id = ?",
                Timestamp.from(NOW),
                who);
    }

    private void reactivate(UUID who) {
        jdbc.update("update workspace_membership set status = 'ACTIVE', deactivated_at = null where user_id = ?", who);
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

    private UUID workspace() {
        return jdbc.query("select id from workspace limit 1", (row, index) -> row.getObject("id", UUID.class)).stream()
                .findFirst()
                .orElseGet(() -> {
                    UUID id = UUID.randomUUID();
                    jdbc.update(
                            "insert into workspace (id, name, workspace_use, singleton, created_at) "
                                    + "values (?, ?, 'AGENCY', true, ?)",
                            id,
                            "Atelier",
                            Timestamp.from(NOW));
                    return id;
                });
    }

    private UUID rootOf(UUID workspace) {
        return jdbc
                .query(
                        "select id from workspace_membership where workspace_id = ? and manager_id is null limit 1",
                        (row, index) -> row.getObject("id", UUID.class),
                        workspace)
                .stream()
                .findFirst()
                .orElseGet(() -> membership(workspace, person("owner-" + UUID.randomUUID() + "@atelier.ro"), null));
    }

    private UUID membership(UUID workspace, UUID who, UUID manager) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into workspace_membership (id, workspace_id, user_id, status, manager_id, joined_at) "
                        + "values (?, ?, ?, 'ACTIVE', ?, ?)",
                id,
                workspace,
                who,
                manager,
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

    private UUID node(UUID jobId, UUID creator) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into work_node (id, job_id, text, creator_id, created_at, state, direction, kind) "
                        + "values (?, ?, 'the brief', ?, ?, 'MARKED', 'STANDALONE', 'JOB_START')",
                id,
                jobId,
                creator,
                Timestamp.from(NOW));
        return id;
    }

    private UUID bracket(UUID performer, String workType, String state, UUID closureRight) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                insert into work_bracket (
                    id, job_id, conversation_id, work_type, performer_ref, opened_by_node,
                    closure_right, state, is_boundary, opened_at, last_activity_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, false, ?, ?)
                """,
                id,
                job,
                conversation,
                workType,
                performer,
                rootNode,
                closureRight,
                state,
                Timestamp.from(NOW),
                Timestamp.from(NOW));
        return id;
    }
}
