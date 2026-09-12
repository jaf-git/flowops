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
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@Tag("WORKSPACE-DEACTIVATE-PERSON-01")
class WorkspaceDeactivationAtomicityTest extends RoundTripTest {
    private static final String OWNER_EMAIL = "maria@atelier.ro";
    private static final String MANAGER_EMAIL = "ionut@atelier.ro";
    private static final String REPORT_EMAIL = "ioana@atelier.ro";
    private static final String PASSWORD = "a-long-enough-passphrase";
    private static final Pattern PASSCODE_IN_BODY = Pattern.compile("code is (\\S+)\\.");

    @MockitoSpyBean
    private AppendWorkspaceEventPort appendWorkspaceEventPort;

    @Test
    void whenTheLastWriteFailsTheMembershipTheReportsAndTheSessionsAreAllUntouched() throws Exception {
        String owner = registerTheOwnerAndSetUp();
        String manager = seedPerson(MANAGER_EMAIL, "Ionuț Petrescu", "MANAGER", owner);
        String report = seedPerson(REPORT_EMAIL, "Ioana Radu", "EMPLOYEE", manager);

        RoundTripClient hisBrowser = signedInAs(MANAGER_EMAIL);
        assertThat(hisBrowser.get("/api/auth/session").getStatusCode()).isEqualTo(HttpStatus.OK);

        doThrow(new IllegalStateException("the event store is unavailable"))
                .when(appendWorkspaceEventPort)
                .append(any());

        ResponseEntity<String> failed = browser.post("/api/workspace/people/" + manager + "/deactivate", "");

        assertThat(failed.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(statusOf(manager))
                .as("access must not have ended, because the deactivation did not happen")
                .isEqualTo("ACTIVE");
        assertThat(managerOf(report))
                .as("the report must not have moved for a deactivation that rolled back")
                .isEqualTo(manager);
        assertThat(hisBrowser.get("/api/workspace/people").getStatusCode())
                .as("a person must not be signed out by a transaction that rolled back")
                .isEqualTo(HttpStatus.OK);
        assertThat(eventCount("PERSON_DEACTIVATED")).isZero();
    }

    private String statusOf(String membershipId) {
        return jdbc.queryForObject(
                "select status from workspace_membership where id = ?::uuid", String.class, membershipId);
    }

    private String managerOf(String membershipId) {
        return jdbc.queryForObject(
                "select manager_id::text from workspace_membership where id = ?::uuid", String.class, membershipId);
    }

    private int eventCount(String action) {
        return jdbc.queryForObject("select count(*) from workspace_event where action = ?", Integer.class, action);
    }

    private RoundTripClient signedInAs(String email) {
        RoundTripClient client = new RoundTripClient(rest);
        client.get("/api/auth/session");
        assertThat(client.post("/api/auth/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        return client;
    }

    private String seedPerson(String email, String displayName, String role, String managerMembership) {
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
                managerMembership,
                OffsetDateTime.now());
        return membershipId.toString();
    }

    private String registerTheOwnerAndSetUp() throws Exception {
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

        return jdbc.queryForObject("select id::text from workspace_membership where manager_id is null", String.class);
    }
}
