package com.flowops.workspace.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.auth.domain.model.EmailAddress;
import com.flowops.support.RoundTripClient;
import com.flowops.support.RoundTripTest;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("WORKSPACE-DEACTIVATE-PERSON-01")
class WorkspaceDeactivatePersonRoundTripTest extends RoundTripTest {
    private static final String OWNER_EMAIL = "maria@atelier.ro";
    private static final String MANAGER_EMAIL = "ionut@atelier.ro";
    private static final String REPORT_EMAIL = "ioana@atelier.ro";
    private static final String DEEP_EMAIL = "andrei@atelier.ro";
    private static final String PASSWORD = "a-long-enough-passphrase";
    private static final Pattern PASSCODE_IN_BODY = Pattern.compile("code is (\\S+)\\.");

    @Test
    void anAnonymousCallerCannotEndAnybodysAccess() {
        ResponseEntity<String> refused = browser.post("/api/workspace/people/" + UUID.randomUUID() + "/deactivate", "");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void theOwnerEndsSomebodysAccessAndTheirReportsMoveUp() throws Exception {
        String owner = registerTheOwnerAndSetUp();
        String manager = seedPerson(MANAGER_EMAIL, "Ionuț Petrescu", "MANAGER", owner);
        String report = seedPerson(REPORT_EMAIL, "Ioana Radu", "MANAGER", manager);
        String deeper = seedPerson(DEEP_EMAIL, "Andrei Munteanu", "EMPLOYEE", report);

        ResponseEntity<String> done = deactivate(manager);

        assertThat(done.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = json.readTree(done.getBody());
        assertThat(body.get("changed").asBoolean()).isTrue();
        assertThat(body.get("reportsMovedTo").asText()).isEqualTo(owner);

        assertThat(statusOf(manager)).isEqualTo("DEACTIVATED");
        assertThat(managerOf(report)).as("the direct report moves up one level").isEqualTo(owner);
        assertThat(managerOf(deeper))
                .as("the subtree moves up one level; it is not flattened")
                .isEqualTo(report);
        assertThat(eventCount("PERSON_DEACTIVATED")).isEqualTo(1);
    }

    @Test
    void aSessionHeldBeforeTheDeactivationStopsWorkingAfterIt() throws Exception {
        String owner = registerTheOwnerAndSetUp();
        String manager = seedPerson(MANAGER_EMAIL, "Ionuț Petrescu", "MANAGER", owner);

        RoundTripClient hisBrowser = signedInAs(MANAGER_EMAIL);
        assertThat(hisBrowser.get("/api/auth/session").getStatusCode())
                .as("he is working when the owner acts")
                .isEqualTo(HttpStatus.OK);

        assertThat(deactivate(manager).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(hisBrowser.get("/api/workspace/people").getStatusCode())
                .as("there is no grace period: his next request is unauthenticated")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void theLastActiveOwnerIsRefusedByTheCountRatherThanByBeingAnOwner() throws Exception {
        String owner = registerTheOwnerAndSetUp();
        String second = seedPerson(MANAGER_EMAIL, "Ionuț Petrescu", "OWNER", owner);

        assertThat(deactivate(second).getStatusCode())
                .as("an owner is deactivated normally while another active owner remains")
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<String> refused = deactivate(owner);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(refused.getBody()).get("code").asText())
                .as("the code is what distinguishes this rule from the permission check")
                .isEqualTo("ONLY_OWNER");
        assertThat(statusOf(owner)).as("nothing changed").isEqualTo("ACTIVE");
    }

    @Test
    void deactivatingSomebodyTwiceChangesNothingTheSecondTimeAndRecordsItOnce() throws Exception {
        String owner = registerTheOwnerAndSetUp();
        String manager = seedPerson(MANAGER_EMAIL, "Ionuț Petrescu", "MANAGER", owner);
        String report = seedPerson(REPORT_EMAIL, "Ioana Radu", "EMPLOYEE", manager);

        assertThat(deactivate(manager).getStatusCode()).isEqualTo(HttpStatus.OK);
        String deactivatedAt = jdbc.queryForObject(
                "select deactivated_at::text from workspace_membership where id = ?::uuid", String.class, manager);

        ResponseEntity<String> again = deactivate(manager);

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(again.getBody()).get("changed").asBoolean())
                .as("the caller is told nothing happened, which the status alone cannot say")
                .isFalse();
        assertThat(eventCount("PERSON_DEACTIVATED")).as("one act, one record").isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select deactivated_at::text from workspace_membership where id = ?::uuid",
                        String.class,
                        manager))
                .as("the moment they left is not rewritten by asking again")
                .isEqualTo(deactivatedAt);
        assertThat(managerOf(report))
                .as("the report was already moved and must not move a second time")
                .isEqualTo(owner);
    }

    @Test
    void somebodyWithoutThePermissionIsRefusedAndTold() throws Exception {
        String owner = registerTheOwnerAndSetUp();
        String employee = seedPerson(REPORT_EMAIL, "Ioana Radu", "EMPLOYEE", owner);

        RoundTripClient herBrowser = signedInAs(REPORT_EMAIL);
        ResponseEntity<String> refused = herBrowser.post("/api/workspace/people/" + owner + "/deactivate", "");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("NOT_PERMITTED");
        assertThat(statusOf(owner)).isEqualTo("ACTIVE");
        assertThat(statusOf(employee)).isEqualTo("ACTIVE");
    }

    private ResponseEntity<String> deactivate(String membershipId) {
        return browser.post("/api/workspace/people/" + membershipId + "/deactivate", "");
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
