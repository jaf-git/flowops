package com.flowops.task.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import com.flowops.auth.domain.model.EmailAddress;
import com.flowops.support.RoundTripClient;
import com.flowops.support.RoundTripTest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("TASK-ACCEPT-01")
class TaskAcceptConcurrencyTest extends RoundTripTest {
    private static final String OWNER_EMAIL = "maria@atelier.ro";
    private static final String PASSWORD = "a-long-enough-passphrase";
    private static final Pattern PASSCODE_IN_BODY = Pattern.compile("code is (\\S+)\\.");

    @Test
    void twoAcceptancesOfOneTaskProduceExactlyOneAcknowledgement() throws Exception {
        registerTheOwnerAndSetUp();
        String mariaMembership =
                jdbc.queryForObject("select id::text from workspace_membership where manager_id is null", String.class);
        UUID andrei = seed("andrei@atelier.ro", "Andrei Munteanu", "EMPLOYEE", mariaMembership)
                .user();

        ResponseEntity<String> created = browser.post(
                "/api/tasks",
                ("{\"title\":\"Draft the supplier review\",\"assigneeId\":\"%s\","
                                + "\"deadline\":\"%s\",\"priority\":\"NORMAL\"}")
                        .formatted(andrei, Instant.now().plusSeconds(86_400)));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String task = com.fasterxml.jackson.databind.json.JsonMapper.builder()
                .build()
                .readTree(created.getBody())
                .get("id")
                .asText();
        String path = "/api/tasks/%s/accept".formatted(task);

        RoundTripClient first = signedInBrowser("andrei@atelier.ro");
        RoundTripClient second = signedInBrowser("andrei@atelier.ro");

        ExecutorService both = Executors.newFixedThreadPool(2);
        try {
            List<Future<ResponseEntity<String>>> answers = both.invokeAll(
                    List.<Callable<ResponseEntity<String>>>of(() -> first.post(path, ""), () -> second.post(path, "")));

            List<HttpStatus> statuses =
                    List.of((HttpStatus) answers.get(0).get().getStatusCode(), (HttpStatus)
                            answers.get(1).get().getStatusCode());

            assertThat(statuses)
                    .as("one acknowledgement succeeds and the other is told the task has moved on")
                    .containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.CONFLICT);
        } finally {
            both.shutdownNow();
        }

        assertThat(jdbc.queryForObject(
                        "select count(*) from task_event where task_id = ?::uuid and action = 'TASK_ACCEPTED'",
                        Long.class,
                        task))
                .as("the log must not claim a task was acknowledged twice")
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                        "select count(*) from task_phase_timer where task_id = ?::uuid and ended_at is null",
                        Long.class,
                        task))
                .as("exactly one phase is open at any moment")
                .isEqualTo(1L);
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
