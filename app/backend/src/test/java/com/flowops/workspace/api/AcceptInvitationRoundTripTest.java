package com.flowops.workspace.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.auth.domain.model.EmailAddress;
import com.flowops.support.RoundTripTest;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;

@Tag("AUTH-ACCEPT-INVITE-01")
class AcceptInvitationRoundTripTest extends RoundTripTest {
    private static final String OWNER_EMAIL = "maria@atelier.ro";
    private static final String PASSWORD = "a-long-enough-passphrase";
    private static final Pattern PASSCODE_IN_BODY = Pattern.compile("code is (\\S+)\\.");
    private static final Pattern TOKEN_IN_LINK = Pattern.compile("token=(\\S+)");

    private int invitationsSent;

    @org.springframework.beans.factory.annotation.Value("${flowops.workspace.invitation.accept-url}")
    private String configuredAcceptUrl;

    @BeforeEach
    void resetWhatThisClassAlsoTracks() {
        invitationsSent = 0;
    }

    @Test
    void theInvitationLinkUsesTheOriginThisInstallationIsConfiguredWith() throws Exception {
        invite("cosmin@atelier.ro", aManagerCalled("Ionuț Petrescu"));

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(10_000).times(invitationsSent)).send(sent.capture());
        String body = sent.getAllValues().getLast().getText();

        assertThat(configuredAcceptUrl).isNotBlank().doesNotContain("${").startsWith("http");
        assertThat(body).contains(configuredAcceptUrl + "?token=");
    }

    @Test
    void anInvitedPersonWithNoAccountSeesWhatTheyAreJoining() throws Exception {
        String token = invite("cosmin@atelier.ro", aManagerCalled("Ionuț Petrescu"));

        browser.forget();
        ResponseEntity<String> opened = browser.get("/api/workspace/invitations/" + token);

        assertThat(opened.getStatusCode())
                .as("an invited person holds no session")
                .isEqualTo(HttpStatus.OK);
        JsonNode body = json.readTree(opened.getBody());
        assertThat(body.get("workspaceName").asText()).isEqualTo("Atelier Ionescu");
        assertThat(body.get("role").asText()).isEqualTo("EMPLOYEE");
        assertThat(body.get("manager").get("displayName").asText()).isEqualTo("Ionuț Petrescu");
        assertThat(body.get("inviter").get("displayName").asText()).isEqualTo("Maria Ionescu");
        assertThat(body.get("consent").get("text").asText()).isNotBlank();
        assertThat(body.get("consent").get("version").asText()).isNotBlank();
    }

    @Test
    void theResponseCarriesNoIdentifiersNoAddressAndNoToken() throws Exception {
        String token = invite("cosmin@atelier.ro", aManagerCalled("Ionuț Petrescu"));

        browser.forget();
        String body = browser.get("/api/workspace/invitations/" + token).getBody();

        assertThat(body).doesNotContain(token);
        assertThat(body).doesNotContain("cosmin@atelier.ro");
        assertThat(body).doesNotContain("maria@atelier.ro");
        assertThat(body)
                .as("no membership, person or workspace identifier")
                .doesNotContainPattern("[0-9a-f]{8}-[0-9a-f]{4}");
    }

    @Test
    void theFiveWaysATokenCanBeUnusableAnswerIdentically() throws Exception {
        String manager = aManagerCalled("Ionuț Petrescu");
        String expired = invite("expired@atelier.ro", manager);
        String used = invite("used@atelier.ro", manager);
        String revoked = invite("revoked@atelier.ro", manager);
        String awaiting = invite("awaiting@atelier.ro", manager);

        setStateOf("expired@atelier.ro", "SENT");
        jdbc.update(
                "update workspace_invitation set expires_at = now() - interval '1 day' where email = ?",
                "expired@atelier.ro");
        setStateOf("used@atelier.ro", "ACCEPTED");
        setStateOf("revoked@atelier.ro", "REVOKED");
        setStateOf("awaiting@atelier.ro", "AWAITING_APPROVAL");

        browser.forget();
        Set<String> answers = new LinkedHashSet<>();
        for (String token : List.of("a-token-that-was-never-minted", expired, used, revoked, awaiting)) {
            ResponseEntity<String> refused = browser.get("/api/workspace/invitations/" + token);
            answers.add(refused.getStatusCode() + " " + refused.getBody());
        }

        assertThat(answers)
                .as("unknown, expired, used, revoked and awaiting approval are one answer, not five")
                .hasSize(1);
        assertThat(answers.iterator().next()).contains(HttpStatus.GONE.toString());
        assertThat(json.readTree(browser.get("/api/workspace/invitations/nothing")
                                .getBody())
                        .get("code")
                        .asText())
                .isEqualTo("INVITATION_NOT_USABLE");
    }

    @Test
    void theRefusalCarriesNoDetailThatCouldDistinguishTheCause() throws Exception {
        browser.forget();

        JsonNode body =
                json.readTree(browser.get("/api/workspace/invitations/nothing").getBody());

        assertThat(body.get("details")).isEmpty();
    }

    @Test
    void aManagerWhoLeftWhileTheInvitationWaitedHandsItToTheirOwnManager() throws Exception {
        String ionut = aManagerCalled("Ionuț Petrescu");
        String ioana = aManagerUnder(ionut, "Ioana Radu");
        String token = invite("cosmin@atelier.ro", ioana);
        jdbc.update(
                "update workspace_membership set status = 'DEACTIVATED', deactivated_at = now() where id = ?::uuid",
                ioana);

        browser.forget();
        JsonNode body =
                json.readTree(browser.get("/api/workspace/invitations/" + token).getBody());

        assertThat(body.get("manager").get("displayName").asText())
                .as("Ionuț, who is above Ioana — not Maria, who is merely the owner")
                .isEqualTo("Ionuț Petrescu");
        assertThat(body.get("manager").get("reassigned").asBoolean())
                .as("shown, never substituted quietly")
                .isTrue();
    }

    private String aManagerUnder(String managerId, String displayName) {
        String email = displayName.toLowerCase().split(" ")[0].replaceAll("[^a-z]", "") + "@atelier.ro";
        invite(email, managerId);
        acceptInto(email, displayName, managerId);
        return jdbc.queryForObject(
                "select m.id::text from workspace_membership m join auth_user u on u.id = m.user_id"
                        + " where u.email = ?",
                String.class,
                email);
    }

    private String aManagerCalled(String displayName) throws Exception {
        registerTheOwnerAndSetUp();
        String owner =
                jdbc.queryForObject("select id::text from workspace_membership where manager_id is null", String.class);
        String email = displayName.toLowerCase().split(" ")[0].replaceAll("[^a-z]", "") + "@atelier.ro";
        String token = invite(email, owner);
        acceptInto(email, displayName, owner);
        return jdbc.queryForObject(
                "select m.id::text from workspace_membership m join auth_user u on u.id = m.user_id"
                        + " where u.email = ?",
                String.class,
                email);
    }

    private void acceptInto(String email, String displayName, String managerId) {
        jdbc.update(
                "insert into auth_user (id, email, account_state, role_name, created_at, setup_completed,"
                        + " display_name) values (gen_random_uuid(), ?, 'ACTIVE', 'MANAGER', now(), true, ?)",
                email,
                displayName);
        jdbc.update(
                "insert into workspace_membership (id, workspace_id, user_id, status, manager_id, joined_at)"
                        + " select gen_random_uuid(), (select id from workspace), u.id, 'ACTIVE', ?::uuid, now()"
                        + " from auth_user u where u.email = ?",
                managerId,
                email);
        jdbc.update("update workspace_invitation set state = 'ACCEPTED' where email = ?", email);
    }

    private void setStateOf(String email, String state) {
        jdbc.update("update workspace_invitation set state = ? where email = ?", state, email);
    }

    private String invite(String email, String managerId) {
        invitationsSent += 1;
        ResponseEntity<String> invited = browser.post(
                "/api/workspace/invitations",
                "{\"emailAddress\":\"%s\",\"role\":\"EMPLOYEE\",\"managerId\":\"%s\"}".formatted(email, managerId));
        assertThat(invited.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(10_000).times(invitationsSent)).send(sent.capture());
        Matcher token = TOKEN_IN_LINK.matcher(
                sent.getAllValues().getLast().getText() == null
                        ? ""
                        : sent.getAllValues().getLast().getText());
        if (!token.find()) {
            throw new AssertionError("no invitation link was delivered to " + email);
        }
        return token.group(1);
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
