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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("WORKSPACE-INVITE-01")
class WorkspaceInvitationRoundTripTest extends RoundTripTest {
    private static final String OWNER_EMAIL = "maria@atelier.ro";
    private static final String COLLEAGUE_EMAIL = "ionut@atelier.ro";
    private static final String PASSWORD = "a-long-enough-passphrase";
    private static final Pattern PASSCODE_IN_BODY = Pattern.compile("code is (\\S+)\\.");

    @Test
    void anAnonymousCallerCannotInviteAnybody() {
        ResponseEntity<String> response = browser.post(
                "/api/workspace/invitations",
                "{\"emailAddress\":\"%s\",\"role\":\"EMPLOYEE\",\"managerId\":\"%s\"}"
                        .formatted(COLLEAGUE_EMAIL, "00000000-0000-0000-0000-000000000001"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(jdbc.queryForObject("select count(*) from workspace_invitation", Long.class))
                .isZero();
    }

    @Test
    void theOwnerInvitesAColleagueOverHttpAndTheInvitationIsStoredWithoutItsToken() throws Exception {
        String managerId = registerSetUpAndFindTheOwnersMembership();

        ResponseEntity<String> invited = browser.post(
                "/api/workspace/invitations",
                "{\"emailAddress\":\"%s\",\"role\":\"EMPLOYEE\",\"managerId\":\"%s\"}"
                        .formatted(COLLEAGUE_EMAIL, managerId));

        assertThat(invited.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = json.readTree(invited.getBody());
        assertThat(body.get("emailAddress").asText()).isEqualTo(COLLEAGUE_EMAIL);
        assertThat(body.get("state").asText()).isEqualTo("SENT");
        assertThat(body.get("expiresAt").asText()).isNotBlank();

        assertThat(invited.getBody()).doesNotContain("token");

        Map<String, Object> stored =
                jdbc.queryForMap("select email, state, token_hash, expires_at from workspace_invitation");
        assertThat(stored.get("email")).isEqualTo(COLLEAGUE_EMAIL);
        assertThat(stored.get("state")).isEqualTo("SENT");

        String hash = (String) stored.get("token_hash");
        assertThat(hash).hasSize(64).matches("[0-9a-f]+");
        assertThat(invited.getBody()).doesNotContain(hash);

        assertThat(jdbc.queryForObject(
                        "select count(*) from workspace_event where action = 'INVITATION_CREATED'", Long.class))
                .isEqualTo(1L);
    }

    @Test
    void aSecondInvitationToTheSameAddressIsRefusedAsADuplicateWhateverTheCase() throws Exception {
        String managerId = registerSetUpAndFindTheOwnersMembership();
        String body = "{\"emailAddress\":\"%s\",\"role\":\"EMPLOYEE\",\"managerId\":\"%s\"}";

        assertThat(browser.post("/api/workspace/invitations", body.formatted(COLLEAGUE_EMAIL, managerId))
                        .getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> again =
                browser.post("/api/workspace/invitations", body.formatted("IONUT@Atelier.ro", managerId));

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(again.getBody()).get("code").asText()).isEqualTo("DUPLICATE_INVITATION");
        assertThat(jdbc.queryForObject("select count(*) from workspace_invitation", Long.class))
                .isEqualTo(1L);
    }

    @Test
    void theOwnerInvitingTheirOwnAddressIsToldTheyAreAlreadyHere() throws Exception {
        String managerId = registerSetUpAndFindTheOwnersMembership();

        ResponseEntity<String> refused = browser.post(
                "/api/workspace/invitations",
                "{\"emailAddress\":\"%s\",\"role\":\"EMPLOYEE\",\"managerId\":\"%s\"}"
                        .formatted(OWNER_EMAIL, managerId));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("SELF_INVITATION");
        assertThat(jdbc.queryForObject("select count(*) from workspace_invitation", Long.class))
                .isZero();
    }

    @Test
    void nobodyCanInviteAnOwner() throws Exception {
        String managerId = registerSetUpAndFindTheOwnersMembership();

        ResponseEntity<String> refused = browser.post(
                "/api/workspace/invitations",
                "{\"emailAddress\":\"cineva@atelier.ro\",\"role\":\"OWNER\",\"managerId\":\"%s\"}"
                        .formatted(managerId));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbc.queryForObject("select count(*) from workspace_invitation", Long.class))
                .isZero();
    }

    private String registerSetUpAndFindTheOwnersMembership() throws Exception {
        registerTheOwner();

        ResponseEntity<String> setUp = browser.post(
                "/api/workspace/setup",
                """
                {"ownerName":"Maria Ionescu","workspaceName":"Atelier Ionescu",
                 "use":"WORK","timezone":"Europe/Bucharest"}
                """);
        assertThat(setUp.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Map<String, Object>> roots =
                jdbc.queryForList("select id from workspace_membership where manager_id is null");
        assertThat(roots)
                .as("setup must create exactly one root membership (I2)")
                .hasSize(1);
        return roots.getFirst().get("id").toString();
    }

    private void registerTheOwner() throws Exception {
        browser.get("/api/auth/session");
        browser.post("/api/auth/signup/passcode", "{\"email\":\"" + OWNER_EMAIL + "\"}");

        ArgumentCaptor<String> delivered = ArgumentCaptor.forClass(String.class);
        verify(mailDispatcher, atLeastOnce()).send(any(EmailAddress.class), anyString(), delivered.capture());
        Matcher code = PASSCODE_IN_BODY.matcher(delivered.getAllValues().getLast());
        if (!code.find()) {
            throw new AssertionError("no passcode was delivered to " + OWNER_EMAIL);
        }

        ResponseEntity<String> signedUp = browser.post(
                "/api/auth/signup",
                "{\"email\":\"%s\",\"passcode\":\"%s\",\"password\":\"%s\"}"
                        .formatted(OWNER_EMAIL, code.group(1), PASSWORD));
        assertThat(signedUp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
