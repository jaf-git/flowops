package com.flowops.workspace.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.auth.domain.model.EmailAddress;
import com.flowops.support.RoundTripTest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("WORKSPACE-VIEW-PEOPLE-01")
class WorkspacePeopleRoundTripTest extends RoundTripTest {
    private static final String OWNER_EMAIL = "maria@atelier.ro";
    private static final String EMPLOYEE_EMAIL = "ioana@atelier.ro";
    private static final String INVITED_EMAIL = "stefan@atelier.ro";
    private static final String PASSWORD = "a-long-enough-passphrase";
    private static final Pattern PASSCODE_IN_BODY = Pattern.compile("code is (\\S+)\\.");

    @Test
    void anAnonymousCallerIsRefusedTheDirectory() {
        ResponseEntity<String> response = browser.get("/api/workspace/people");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void theOwnerAloneIsToldSoRatherThanShownATreeOfOne() throws Exception {
        registerTheOwnerAndSetUp();

        ResponseEntity<String> response = browser.get("/api/workspace/people");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = json.readTree(response.getBody());
        assertThat(body.get("onlyMember").asBoolean()).isTrue();
        assertThat(body.get("people")).hasSize(1);

        JsonNode maria = body.get("people").get(0);
        assertThat(maria.get("displayName").asText()).isEqualTo("Maria Ionescu");
        assertThat(maria.get("role").asText()).isEqualTo("OWNER");
        assertThat(maria.get("isSelf").asBoolean()).isTrue();
        assertThat(maria.get("managerId").isNull())
                .as("the owner is the root (I2)")
                .isTrue();
    }

    @Test
    void aDirectoryRowCarriesNoAddress() throws Exception {
        registerTheOwnerAndSetUp();
        seedAnEmployee();

        ResponseEntity<String> response = browser.get("/api/workspace/people");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).doesNotContain(OWNER_EMAIL).doesNotContain(EMPLOYEE_EMAIL);
    }

    @Test
    void theOwnerSeesAPendingInvitationBesideTheMembers() throws Exception {
        String ownerMembership = registerTheOwnerAndSetUp();
        invite(INVITED_EMAIL, ownerMembership);

        ResponseEntity<String> response = browser.get("/api/workspace/people");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode invitations = json.readTree(response.getBody()).get("invitations");
        assertThat(invitations).hasSize(1);
        assertThat(invitations.get(0).get("emailAddress").asText()).isEqualTo(INVITED_EMAIL);
        assertThat(invitations.get(0).get("state").asText()).isEqualTo("SENT");
        assertThat(invitations.get(0).get("intendedManagerId").asText()).isEqualTo(ownerMembership);

        assertThat(response.getBody()).doesNotContain("token");
    }

    @Test
    void anEmployeeReachesTheDirectoryBecausePeopleViewIsGrantedToTheirRoleToo() throws Exception {
        registerTheOwnerAndSetUp();
        seedAnEmployee();
        browser.forget();
        signIn(EMPLOYEE_EMAIL);

        ResponseEntity<String> response = browser.get("/api/workspace/people");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = json.readTree(response.getBody());
        assertThat(body.get("people")).hasSize(2);
        assertThat(body.get("onlyMember").asBoolean()).isFalse();
    }

    @Test
    void aCallerWhoDoesNotHoldPeopleViewIsRefusedTheDirectory() throws Exception {
        registerTheOwnerAndSetUp();
        seedAnEmployee();
        jdbc.update(
                "delete from auth_role_permission where role_name = 'EMPLOYEE' and permission_name = 'PEOPLE_VIEW'");
        try {
            browser.forget();
            signIn(EMPLOYEE_EMAIL);

            assertThat(browser.get("/api/workspace/people").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        } finally {
            jdbc.update(
                    "insert into auth_role_permission (role_name, permission_name) values ('EMPLOYEE', 'PEOPLE_VIEW')");
        }
    }

    @Test
    void anEmployeeIsToldNothingAboutAnUnannouncedHire() throws Exception {
        String ownerMembership = registerTheOwnerAndSetUp();
        seedAnEmployee();
        invite(INVITED_EMAIL, ownerMembership);
        browser.forget();
        signIn(EMPLOYEE_EMAIL);

        ResponseEntity<String> response = browser.get("/api/workspace/people");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .as("no part of an invitation may reach a viewer without PERSON_INVITE")
                .doesNotContain(INVITED_EMAIL);
        assertThat(json.readTree(response.getBody()).has("invitations"))
                .as("absent entirely, not an empty list")
                .isFalse();
    }

    @Test
    void aDeactivatedPersonCarriesTheDateTheirAccessEnded() throws Exception {
        registerTheOwnerAndSetUp();
        seedAnEmployee();
        jdbc.update(
                "update workspace_membership set status = 'DEACTIVATED', deactivated_at = ?"
                        + " where user_id = (select id from auth_user where email = ?)",
                OffsetDateTime.now().minusDays(30),
                EMPLOYEE_EMAIL);

        ResponseEntity<String> response = browser.get("/api/workspace/people");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode ioana = json.readTree(response.getBody()).get("people").get(1);
        assertThat(ioana.get("status").asText()).isEqualTo("DEACTIVATED");
        assertThat(ioana.get("deactivatedAt").isNull())
                .as("extension 2c marks them deactivated *with the date*")
                .isFalse();
    }

    @Test
    void anInvitationWhoseExpiryHasPassedIsNoLongerPending() throws Exception {
        String ownerMembership = registerTheOwnerAndSetUp();
        invite(INVITED_EMAIL, ownerMembership);
        jdbc.update(
                "update workspace_invitation set expires_at = ?",
                OffsetDateTime.now().minusDays(1));

        ResponseEntity<String> response = browser.get("/api/workspace/people");

        assertThat(json.readTree(response.getBody()).get("invitations")).isEmpty();
        assertThat(response.getBody()).doesNotContain(INVITED_EMAIL);
    }

    private void invite(String email, String managerId) {
        ResponseEntity<String> invited = browser.post(
                "/api/workspace/invitations",
                "{\"emailAddress\":\"%s\",\"role\":\"EMPLOYEE\",\"managerId\":\"%s\"}".formatted(email, managerId));
        assertThat(invited.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private void seedAnEmployee() {
        UUID userId = UUID.randomUUID();
        UUID workspaceId = jdbc.queryForObject("select id from workspace", UUID.class);
        UUID ownerMembership =
                jdbc.queryForObject("select id from workspace_membership where manager_id is null", UUID.class);

        jdbc.update(
                "insert into auth_user (id, email, account_state, role_name, created_at, setup_completed, display_name)"
                        + " values (?, ?, 'ACTIVE', 'EMPLOYEE', ?, true, ?)",
                userId,
                EMPLOYEE_EMAIL,
                OffsetDateTime.now(),
                "Ioana Radu");
        jdbc.update(
                "insert into auth_credential (user_id, password_hash, algorithm, updated_at) values (?, ?, 'bcrypt', ?)",
                userId,
                seededHashOf(PASSWORD),
                OffsetDateTime.now());
        jdbc.update(
                "insert into workspace_membership (id, workspace_id, user_id, status, manager_id, joined_at)"
                        + " values (?, ?, ?, 'ACTIVE', ?, ?)",
                UUID.randomUUID(),
                workspaceId,
                userId,
                ownerMembership,
                OffsetDateTime.now());
    }

    private void signIn(String email) {
        browser.get("/api/auth/session");
        ResponseEntity<String> signedIn =
                browser.post("/api/auth/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD));
        assertThat(signedIn.getStatusCode()).isEqualTo(HttpStatus.OK);
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

        List<Map<String, Object>> roots =
                jdbc.queryForList("select id from workspace_membership where manager_id is null");
        assertThat(roots)
                .as("setup must create exactly one root membership (I2)")
                .hasSize(1);
        return roots.getFirst().get("id").toString();
    }
}
