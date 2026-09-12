package com.flowops.task.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.flowops.auth.domain.model.EmailAddress;
import com.flowops.support.RoundTripClient;
import com.flowops.support.RoundTripTest;
import com.flowops.task.application.shared.port.AppendTaskEventPort;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@Tag("TASK-CREATE-01")
class TaskCreationAtomicityTest extends RoundTripTest {
    private static final String OWNER_EMAIL = "maria@atelier.ro";
    private static final String PASSWORD = "a-long-enough-passphrase";
    private static final Pattern PASSCODE_IN_BODY = Pattern.compile("code is (\\S+)\\.");

    @MockitoSpyBean
    private AppendTaskEventPort taskEvents;

    @Test
    void whenTheEventCannotBeAppendedNoTaskNoPhaseAndNoTransitionSurvive() throws Exception {
        registerTheOwnerAndSetUp();
        String mariaMembership =
                jdbc.queryForObject("select id::text from workspace_membership where manager_id is null", String.class);
        UUID andrei = seed("andrei@atelier.ro", "Andrei Munteanu", "EMPLOYEE", mariaMembership)
                .user();

        doThrow(new IllegalStateException("the event log is unreachable"))
                .when(taskEvents)
                .append(any());

        ResponseEntity<String> attempted = browser.post(
                "/api/tasks",
                ("{\"title\":\"Draft the supplier review\",\"assigneeId\":\"%s\","
                                + "\"deadline\":\"%s\",\"priority\":\"NORMAL\"}")
                        .formatted(andrei, Instant.now().plusSeconds(86_400)));

        assertThat(attempted.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        assertThat(jdbc.queryForObject("select count(*) from task", Long.class))
                .as("the task must not survive a transaction that unwound")
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from task_phase_timer", Long.class))
                .as("a clock running on a task that does not exist is the failure this rule prevents")
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from task_state_transition", Long.class))
                .as("a lifecycle with no task is a lifecycle nobody can read")
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from task_event", Long.class))
                .isZero();
    }

    private record Seeded(UUID user, String membership) {}

    private Seeded seed(String email, String displayName, String role, String managerId) {
        UUID userId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        UUID workspaceId = jdbc.queryForObject("select id from workspace", UUID.class);

        jdbc.update(
                "insert into auth_user (id, email, account_state, role_name, created_at, setup_completed, display_name)"
                        + " values (?, ?, 'ACTIVE', ?, ?, true, ?)",
                userId,
                email,
                role,
                OffsetDateTime.now(),
                displayName);
        jdbc.update(
                "insert into auth_credential (user_id, password_hash, algorithm, updated_at) values (?, ?, 'bcrypt', ?)",
                userId,
                seededHashOf(PASSWORD),
                OffsetDateTime.now());
        jdbc.update(
                "insert into workspace_membership (id, workspace_id, user_id, status, manager_id, joined_at)"
                        + " values (?, ?, ?, 'ACTIVE', ?::uuid, ?)",
                membershipId,
                workspaceId,
                userId,
                managerId,
                OffsetDateTime.now().minusDays(90));
        return new Seeded(userId, membershipId.toString());
    }

    private RoundTripClient signedInBrowser(String email) {
        RoundTripClient client = new RoundTripClient(rest);
        client.get("/api/auth/session");
        assertThat(client.post("/api/auth/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        return client;
    }

    private void registerTheOwnerAndSetUp() throws Exception {
        browser.get("/api/auth/session");
        browser.post("/api/auth/signup/passcode", "{\"email\":\"" + OWNER_EMAIL + "\"}");

        ArgumentCaptor<String> delivered = ArgumentCaptor.forClass(String.class);
        verify(mailDispatcher, atLeastOnce()).send(any(EmailAddress.class), anyString(), delivered.capture());
        Matcher code = PASSCODE_IN_BODY.matcher(delivered.getAllValues().getLast());
        if (!code.find()) {
            throw new AssertionError("no passcode was delivered to " + OWNER_EMAIL);
        }

        assertThat(browser.post(
                                "/api/auth/signup",
                                "{\"email\":\"%s\",\"passcode\":\"%s\",\"password\":\"%s\"}"
                                        .formatted(OWNER_EMAIL, code.group(1), PASSWORD))
                        .getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(browser.post(
                                "/api/workspace/setup",
                                """
                                {"ownerName":"Maria Ionescu","workspaceName":"Atelier Ionescu",
                                 "use":"WORK","timezone":"Europe/Bucharest"}
                                """)
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }
}
