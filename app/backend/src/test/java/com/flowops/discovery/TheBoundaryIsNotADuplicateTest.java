package com.flowops.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowops.support.ApplicationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class TheBoundaryIsNotADuplicateTest extends ApplicationTest {
    @Autowired
    private JdbcTemplate jdbc;

    private static final Instant NOW = Instant.parse("2026-08-26T09:00:00Z");

    private UUID omar;
    private UUID job;
    private UUID conversation;
    private UUID rootNode;

    @BeforeEach
    void aJobInTheTeamChannel() {
        omar = person("omar-" + UUID.randomUUID() + "@atelier.ro");
        job = job("Sunrise Bakery · summer menu", omar);
        conversation = UUID.randomUUID();
        rootNode = node(job, omar);
    }

    @Test
    @DisplayName("R1.1 — a second open bracket at one address is refused by the database")
    void oneOpenBracketPerAddress() {
        bracket(omar, "PHOTO", "OPEN", false);

        assertThatThrownBy(() -> bracket(omar, "PHOTO", "OPEN", false))
                .describedAs("R1.1 - joining is the default; a second bracket at one address is the "
                        + "fragmentation the whole keying design exists to prevent")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("R2 — two performers at an otherwise identical address are two brackets")
    void thePerformerIsPartOfTheKey() {
        UUID layla = person("layla-" + UUID.randomUUID() + "@atelier.ro");

        bracket(omar, "CONTENT", "OPEN", false);

        assertThatCode(() -> bracket(layla, "CONTENT", "OPEN", false))
                .describedAs("D2 - two writers under one manager must never merge into one bracket")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("R4a.1 — real work opens beside the boundary at the identical address")
    void realWorkOpensBesideTheBoundary() {
        bracket(omar, "CLIENT_INTAKE", "OPEN", true);

        assertThatCode(() -> bracket(omar, "CLIENT_INTAKE", "OPEN", false))
                .describedAs("R4a.1 - the boundary is the job container. Work matching its address opens "
                        + "beside it; refusing that row loses a genuine step from the learned process")
                .doesNotThrowAnyException();

        assertThat(openBracketsAt("CLIENT_INTAKE"))
                .describedAs("one container and one piece of work, not one of either")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("R1 — a closed bracket releases its address")
    void aClosedBracketFreesItsAddress() {
        UUID first = bracket(omar, "DESIGN", "OPEN", false);
        jdbc.update(
                "update work_bracket set state = 'CLOSED', close_kind = 'DONE', closed_at = ? where id = ?",
                Timestamp.from(NOW),
                first);

        assertThatCode(() -> bracket(omar, "DESIGN", "OPEN", false)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("R15.3 — JOB_END is a close kind the schema accepts")
    void theBoundaryCanBeEnded() {
        UUID boundary = bracket(omar, "CLIENT_INTAKE", "OPEN", true);

        assertThatCode(() -> jdbc.update(
                        "update work_bracket set state = 'CLOSED', close_kind = 'JOB_END', closed_at = ? "
                                + "where id = ?",
                        Timestamp.from(NOW),
                        boundary))
                .describedAs("R15.3 - closing a job writes JOB_END on the boundary; without it the root "
                        + "was openable and unclosable, and no job could ever produce a shape")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("R16.4 — a force-closed engagement with no reason is not a row that can exist")
    void aForcedEndingSaysWhyInTheSchema() {
        assertThatThrownBy(() -> jdbc.update(
                        "update job set status = 'FORCE_CLOSED', closed_at = ?, shape_eligible = false "
                                + "where id = ?",
                        Timestamp.from(NOW),
                        job))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("D12 — a force-closed engagement cannot be shape-eligible")
    void aForcedEndingTeachesNothing() {
        assertThatThrownBy(() -> jdbc.update(
                        "update job set status = 'FORCE_CLOSED', closed_at = ?, close_reason = 'budget pulled', "
                                + "shape_eligible = true where id = ?",
                        Timestamp.from(NOW),
                        job))
                .describedAs("the exclusion from shape evidence depends on a forced job being "
                        + "distinguishable from a clean one")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("R15 — a job state nothing recognises is refused rather than read as something else")
    void anUnknownStateIsRefused() {
        assertThatThrownBy(() -> jdbc.update("update job set status = 'FINISHED' where id = ?", job))
                .isInstanceOf(DataIntegrityViolationException.class);
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

    private UUID node(UUID jobId, UUID creator) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into work_node (id, job_id, text, creator_id, created_at, state, direction, kind) "
                        + "values (?, ?, 'Dana''s brief', ?, ?, 'MARKED', 'STANDALONE', 'JOB_START')",
                id,
                jobId,
                creator,
                Timestamp.from(NOW));
        return id;
    }

    private UUID bracket(UUID performer, String workType, String state, boolean isBoundary) {
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
                rootNode,
                performer,
                state,
                isBoundary,
                Timestamp.from(NOW),
                Timestamp.from(NOW));
        return id;
    }

    private int openBracketsAt(String workType) {
        return jdbc.queryForObject(
                "select count(*) from work_bracket where job_id = ? and work_type = ? "
                        + "and state in ('OPEN', 'WAITING')",
                Integer.class,
                job,
                workType);
    }
}
