package com.flowops.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.notification.application.shared.port.RecipientStatePort;
import com.flowops.support.ApplicationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class WhoIsStillHereIsAskedOfTheDatabaseTest extends ApplicationTest {
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RecipientStatePort recipients;

    private static final Instant NOW = Instant.parse("2026-08-29T09:00:00Z");

    @Test
    @DisplayName("I13 — an active member is here, a deactivated one is not, and a stranger is not either")
    void membershipDecidesIt() {
        UUID workspace = workspace();
        UUID root = root(workspace);

        UUID sara = person("sara-i13");
        UUID nour = person("nour-i13");
        membership(workspace, sara, root, false);
        membership(workspace, nour, root, true);
        UUID strangerWithNoMembership = person("stranger-i13");

        Set<UUID> stillHere = recipients.stillHere(Set.of(sara, nour, strangerWithNoMembership));

        assertThat(stillHere)
                .as("an active membership is the only thing that counts as being here")
                .containsExactly(sara);
        assertThat(stillHere)
                .as("I13 - somebody who has left is not delivered to")
                .doesNotContain(nour)
                .doesNotContain(strangerWithNoMembership);
    }

    @Test
    @DisplayName("nothing due asks nothing, rather than asking a question Postgres refuses")
    void anEmptyBatchIsNotAQuery() {
        assertThat(recipients.stillHere(Set.of())).isEmpty();
    }

    private UUID workspace() {
        List<UUID> existing =
                jdbc.query("select id from workspace limit 1", (rows, index) -> rows.getObject("id", UUID.class));
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into workspace (id, name, singleton, created_at) values (?, 'Atelier', true, ?)",
                id,
                Timestamp.from(NOW));
        return id;
    }

    private UUID root(UUID workspace) {
        List<UUID> existing = jdbc.query(
                "select id from workspace_membership where workspace_id = ? and manager_id is null limit 1",
                (rows, index) -> rows.getObject("id", UUID.class),
                workspace);

        return existing.isEmpty() ? membership(workspace, person("root-i13"), null, false) : existing.get(0);
    }

    private UUID membership(UUID workspace, UUID user, UUID manager, boolean hasLeft) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                insert into workspace_membership
                       (id, workspace_id, user_id, status, joined_at, deactivated_at, manager_id)
                values (?, ?, ?, 'ACTIVE', ?, ?, ?)
                """,
                id,
                workspace,
                user,
                Timestamp.from(NOW),
                hasLeft ? Timestamp.from(NOW) : null,
                manager);
        return id;
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
}
