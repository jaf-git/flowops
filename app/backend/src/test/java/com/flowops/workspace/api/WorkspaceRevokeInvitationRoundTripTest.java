package com.flowops.workspace.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.auth.domain.model.EmailAddress;
import com.flowops.support.RoundTripTest;
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

@Tag("WORKSPACE-REVOKE-INVITE-01")
class WorkspaceRevokeInvitationRoundTripTest extends RoundTripTest {
    private static final String OWNER_EMAIL = "maria@atelier.ro";
    private static final String INVITED_EMAIL = "ionut@atelier.ro";
    private static final String PASSWORD = "a-long-enough-passphrase";
    private static final Pattern PASSCODE_IN_BODY = Pattern.compile("code is (\\S+)\\.");

    @Test
    void anAnonymousCallerCannotRevokeAnything() {
        ResponseEntity<String> response =
                browser.post("/api/workspace/invitations/%s/revoke".formatted(UUID.randomUUID()), "");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void theOwnerWithdrawsAnInvitationAndItLeavesTheDirectory() throws Exception {
        String invitation = registerSetUpAndInvite();

        ResponseEntity<String> revoked = browser.post("/api/workspace/invitations/%s/revoke".formatted(invitation), "");

        assertThat(revoked.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = json.readTree(revoked.getBody());
        assertThat(body.get("state").asText()).isEqualTo("REVOKED");
        assertThat(body.get("emailAddress").asText()).isEqualTo(INVITED_EMAIL);
        assertThat(body.get("revokedAt").asText()).isNotBlank();

        assertThat(revoked.getBody()).doesNotContain("token");

        assertThat(jdbc.queryForObject(
                        "select state from workspace_invitation where id = ?::uuid", String.class, invitation))
                .isEqualTo("REVOKED");
        assertThat(jdbc.queryForObject(
                        "select count(*) from workspace_event where action = 'INVITATION_REVOKED'", Long.class))
                .isEqualTo(1L);

        JsonNode directory = json.readTree(browser.get("/api/workspace/people").getBody());
        assertThat(directory.get("invitations")).isEmpty();
        assertThat(browser.get("/api/workspace/people").getBody()).doesNotContain(INVITED_EMAIL);
    }

    @Test
    void revokingTwiceAnswersSuccessAndAppendsOneEvent() throws Exception {
        String invitation = registerSetUpAndInvite();
        String path = "/api/workspace/invitations/%s/revoke".formatted(invitation);

        assertThat(browser.post(path, "").getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> again = browser.post(path, "");

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(again.getBody()).get("state").asText()).isEqualTo("REVOKED");
        assertThat(json.readTree(again.getBody()).get("revokedAt").isNull())
                .as("the second call changed nothing, so it revoked nothing")
                .isTrue();
        assertThat(jdbc.queryForObject(
                        "select count(*) from workspace_event where action = 'INVITATION_REVOKED'", Long.class))
                .isEqualTo(1L);
    }

    @Test
    void anIdentifierNamingNoInvitationIsRefusedRatherThanAnsweredWithSuccess() throws Exception {
        registerSetUpAndInvite();

        ResponseEntity<String> response =
                browser.post("/api/workspace/invitations/%s/revoke".formatted(UUID.randomUUID()), "");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(json.readTree(response.getBody()).get("code").asText()).isEqualTo("INVITATION_NOT_FOUND");
    }

    @Test
    void aCallerHoldingNeitherRevokePermissionIsRefused() throws Exception {
        String invitation = registerSetUpAndInvite();
        jdbc.update("delete from auth_role_permission where role_name = 'OWNER'"
                + " and permission_name in ('INVITATION_REVOKE_OWN', 'INVITATION_REVOKE_ANY')");
        try {
            browser.forget();
            signInAsTheOwner();

            ResponseEntity<String> refused =
                    browser.post("/api/workspace/invitations/%s/revoke".formatted(invitation), "");

            assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

            assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("NOT_PERMITTED");
            assertThat(jdbc.queryForObject(
                            "select state from workspace_invitation where id = ?::uuid", String.class, invitation))
                    .as("a refused call changes nothing")
                    .isEqualTo("SENT");
        } finally {
            jdbc.update("insert into auth_role_permission (role_name, permission_name)"
                    + " values ('OWNER', 'INVITATION_REVOKE_OWN'), ('OWNER', 'INVITATION_REVOKE_ANY')");
        }
    }

    private String registerSetUpAndInvite() throws Exception {
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

        ResponseEntity<String> invited = browser.post(
                "/api/workspace/invitations",
                "{\"emailAddress\":\"%s\",\"role\":\"EMPLOYEE\",\"managerId\":\"%s\"}"
                        .formatted(INVITED_EMAIL, roots.getFirst().get("id")));
        assertThat(invited.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(invited.getBody()).get("id").asText();
    }

    private void signInAsTheOwner() {
        browser.get("/api/auth/session");
        assertThat(browser.post(
                                "/api/auth/login",
                                "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(OWNER_EMAIL, PASSWORD))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }
}
