package com.flowops.workspace.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.flowops.auth.domain.model.EmailAddress;
import com.flowops.support.RoundTripClient;
import com.flowops.support.RoundTripTest;
import com.flowops.workspace.application.shared.port.AppendWorkspaceEventPort;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@Tag("WORKSPACE-EDIT-REPORTING-LINE-01")
class WorkspaceReassignConcurrencyTest extends RoundTripTest {
    private static final String OWNER_EMAIL = "maria@atelier.ro";
    private static final String PASSWORD = "a-long-enough-passphrase";
    private static final Pattern PASSCODE_IN_BODY = Pattern.compile("code is (\\S+)\\.");

    @MockitoSpyBean
    private AppendWorkspaceEventPort workspacePersistence;

    @Test
    void twoMovesThatWouldCloseALoopLeaveTheTreeRootedAtTheOwner() throws Exception {
        Company company = buildTheCompany();

        RoundTripClient laptop = signedInBrowser();
        RoundTripClient phone = signedInBrowser();

        ExecutorService threads = Executors.newFixedThreadPool(2);
        List<ResponseEntity<String>> answers;
        try {
            Callable<ResponseEntity<String>> moveIonutUnderIoana = () -> laptop.post(
                    "/api/workspace/people/%s/manager".formatted(company.ionut),
                    "{\"proposedManagerId\":\"%s\"}".formatted(company.ioana));
            Callable<ResponseEntity<String>> moveIoanaUnderIonut = () -> phone.post(
                    "/api/workspace/people/%s/manager".formatted(company.ioana),
                    "{\"proposedManagerId\":\"%s\"}".formatted(company.ionut));

            answers = threads.invokeAll(List.of(moveIonutUnderIoana, moveIoanaUnderIonut)).stream()
                    .map(WorkspaceReassignConcurrencyTest::get)
                    .toList();
        } finally {
            threads.shutdownNow();
        }

        long succeeded = answers.stream()
                .filter(answer -> answer.getStatusCode() == HttpStatus.OK)
                .count();
        assertThat(succeeded).as("exactly one of the two moves may take effect").isEqualTo(1L);

        assertThat(everyMembershipReachesTheOwner())
                .as("invariant I1 holds whichever move won")
                .isTrue();
        assertThat(jdbc.queryForObject(
                        "select count(*) from workspace_event where action = 'REPORTING_LINE_CHANGED'", Long.class))
                .isEqualTo(1L);
    }

    @Test
    void whenTheEventCannotBeAppendedTheMoveDoesNotHappenEither() throws Exception {
        Company company = buildTheCompany();
        doThrow(new IllegalStateException("the event log is unavailable"))
                .when(workspacePersistence)
                .append(any());

        ResponseEntity<String> response = browser.post(
                "/api/workspace/people/%s/manager".formatted(company.ioana),
                "{\"proposedManagerId\":\"%s\"}".formatted(company.ionut));

        assertThat(response.getStatusCode().is5xxServerError()).isTrue();
        assertThat(jdbc.queryForObject(
                        "select manager_id from workspace_membership where id = ?::uuid", String.class, company.ioana))
                .as("a move whose event could not be recorded must not survive")
                .isEqualTo(company.maria);
    }

    private boolean everyMembershipReachesTheOwner() {
        Map<String, String> managerOf = new HashMap<>();
        jdbc.queryForList("select id::text as id, manager_id::text as manager from workspace_membership")
                .forEach(row -> managerOf.put((String) row.get("id"), (String) row.get("manager")));

        for (String start : managerOf.keySet()) {
            String cursor = managerOf.get(start);
            int steps = 0;
            while (cursor != null) {
                if (++steps > managerOf.size()) {
                    return false;
                }
                cursor = managerOf.get(cursor);
            }
        }
        return true;
    }

    private static ResponseEntity<String> get(Future<ResponseEntity<String>> future) {
        try {
            return future.get();
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private record Company(String maria, String ionut, String ioana) {}

    private RoundTripClient signedInBrowser() {
        RoundTripClient client = new RoundTripClient(rest);
        client.get("/api/auth/session");
        assertThat(client.post(
                                "/api/auth/login",
                                "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(OWNER_EMAIL, PASSWORD))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        return client;
    }

    private Company buildTheCompany() throws Exception {
        registerTheOwnerAndSetUp();
        String maria =
                jdbc.queryForObject("select id::text from workspace_membership where manager_id is null", String.class);
        String ionut = seed("ionut@atelier.ro", "Ionuț Petrescu", maria);
        String ioana = seed("ioana@atelier.ro", "Ioana Radu", maria);
        return new Company(maria, ionut, ioana);
    }

    private String seed(String email, String displayName, String managerId) {
        UUID userId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        UUID workspaceId = jdbc.queryForObject("select id from workspace", UUID.class);

        jdbc.update(
                "insert into auth_user (id, email, account_state, role_name, created_at, setup_completed, display_name)"
                        + " values (?, ?, 'ACTIVE', 'MANAGER', ?, true, ?)",
                userId,
                email,
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
        return membershipId.toString();
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
