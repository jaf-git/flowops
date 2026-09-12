package com.flowops.workspace.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.auth.domain.model.EmailAddress;
import com.flowops.support.RoundTripClient;
import com.flowops.support.RoundTripTest;
import com.flowops.workspace.application.shared.port.AppendWorkspaceEventPort;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@Tag("AUTH-ACCEPT-INVITE-01")
class AcceptInvitationWriteRoundTripTest extends RoundTripTest {
    private static final String OWNER_EMAIL = "maria.enache@atelier.ro";
    private static final String COSMIN = "cosmin.ionescu@atelier.ro";
    private static final String IOANA = "ioana.radu@atelier.ro";
    private static final String PASSWORD = "a-long-enough-passphrase";
    private static final String COSMIN_NAME = "Cosmin Ionescu";
    private static final Pattern PASSCODE_IN_BODY = Pattern.compile("code is (\\S+)\\.");
    private static final Pattern TOKEN_IN_BODY = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @MockitoSpyBean
    private AppendWorkspaceEventPort appendWorkspaceEventPort;

    @BeforeEach
    void resetWhatThisClassAlsoTracks() {
        reset(appendWorkspaceEventPort, mailSender);
    }

    @Test
    void anInvitedPersonJoinsAndAppearsInTheDirectoryUnderTheNameTheyGave() throws Exception {
        String token = anInvitationSentTo(COSMIN);
        RoundTripClient theirBrowser = aClientThatHasOpenedTheirLink(token);
        String version = consentVersionShownTo(theirBrowser, token);

        ResponseEntity<String> joined = accept(theirBrowser, token, COSMIN_NAME, PASSWORD, version);

        assertThat(joined.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = json.readTree(joined.getBody());
        assertThat(body.get("email").asText()).isEqualTo(COSMIN);
        assertThat(body.get("landingTarget").asText()).isEqualTo("MY_WORK");
        assertThat(theirBrowser.holdsSession())
                .as("acceptance must leave them signed in, not at a login screen")
                .isTrue();

        assertThat(storedDisplayNameOf(COSMIN)).isEqualTo(COSMIN_NAME);
        assertThat(storedStateOf(COSMIN)).isEqualTo("ACCEPTED");

        ResponseEntity<String> directory = browser.get("/api/workspace/people");
        assertThat(directory.getBody())
                .as("the person joined and is missing from the directory the owner reads")
                .contains(COSMIN_NAME);

        assertThat(managerOfTheMembershipFor(COSMIN))
                .as("the person must be attached to the manager their invitation named")
                .isEqualTo(ownerMembershipId());
        assertThat(recordedActions()).contains("INVITATION_ACCEPTED");
        assertThat(consentRecordCount()).isEqualTo(1);
        assertThat(membershipCountFor(COSMIN)).isEqualTo(1);
    }

    @Test
    void whenAnyPartOfAcceptanceFailsNoneOfItExists() throws Exception {
        String token = anInvitationSentTo(COSMIN);
        RoundTripClient theirBrowser = aClientThatHasOpenedTheirLink(token);
        String version = consentVersionShownTo(theirBrowser, token);
        int sessionsBefore = jdbc.queryForObject("select count(*) from spring_session", Integer.class);
        doThrow(new IllegalStateException("the event append failed"))
                .when(appendWorkspaceEventPort)
                .append(any());

        accept(theirBrowser, token, COSMIN_NAME, PASSWORD, version);

        assertThat(accountCountFor(COSMIN))
                .as("an account survived a failed acceptance")
                .isZero();
        assertThat(credentialCount()).isZero();
        assertThat(membershipCountFor(COSMIN)).isZero();
        assertThat(consentRecordCount())
                .as("a consent record survived a failed acceptance")
                .isZero();
        assertThat(storedStateOf(COSMIN))
                .as("the invitation must still be usable, because nothing was created from it")
                .isEqualTo("SENT");

        assertThat(jdbc.queryForObject("select count(*) from spring_session", Integer.class))
                .as("a failed acceptance left a session behind, authenticating an account that no longer exists")
                .isEqualTo(sessionsBefore);
        assertThat(theirBrowser.holdsSession())
                .as("they were told the join failed; they must not be holding a session that says otherwise")
                .isFalse();
    }

    @Test
    void aPasswordFailingPolicyNamesTheRuleAndCreatesNothing() throws Exception {
        String token = anInvitationSentTo(COSMIN);
        RoundTripClient theirBrowser = aClientThatHasOpenedTheirLink(token);
        String version = consentVersionShownTo(theirBrowser, token);

        ResponseEntity<String> refused = accept(theirBrowser, token, COSMIN_NAME, "short", version);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode body = json.readTree(refused.getBody());
        assertThat(body.get("code").asText()).isEqualTo("PASSWORD_POLICY_VIOLATION");
        assertThat(body.get("details").get(0).get("field").asText()).isEqualTo("password");
        assertThat(body.get("details").get(0).get("rule").asText())
                .as("the rule name the frontend maps to a message key")
                .isEqualTo("MINIMUM_LENGTH");
        assertThat(accountCountFor(COSMIN)).isZero();
        assertThat(storedStateOf(COSMIN))
                .as("a typo must not cost them the link; the invitation is not spent by a refused password")
                .isEqualTo("SENT");
    }

    @Test
    void aBlankPasswordNamesTheRuleRatherThanAnsweringGenerically() throws Exception {
        String token = anInvitationSentTo(COSMIN);
        RoundTripClient theirBrowser = aClientThatHasOpenedTheirLink(token);
        String version = consentVersionShownTo(theirBrowser, token);

        ResponseEntity<String> refused = accept(theirBrowser, token, COSMIN_NAME, "", version);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json.readTree(refused.getBody()).get("code").asText())
                .as("a person who left the password empty is told which rule they missed")
                .isEqualTo("PASSWORD_POLICY_VIOLATION");
        assertThat(accountCountFor(COSMIN)).isZero();
        assertThat(storedStateOf(COSMIN))
                .as("an empty password must not spend the invitation either")
                .isEqualTo("SENT");
    }

    @Test
    void anEmptyNameIsRefusedAndCreatesNothing() throws Exception {
        String token = anInvitationSentTo(COSMIN);
        RoundTripClient theirBrowser = aClientThatHasOpenedTheirLink(token);
        String version = consentVersionShownTo(theirBrowser, token);

        ResponseEntity<String> refused = accept(theirBrowser, token, "   ", PASSWORD, version);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("DISPLAY_NAME_REQUIRED");
        assertThat(accountCountFor(COSMIN)).isZero();
    }

    @Test
    void anAddressThatAlreadyHoldsAnAccountIsToldToSignInRatherThanMeetingAConstraint() throws Exception {
        String token = anInvitationSentTo(COSMIN);
        RoundTripClient theirBrowser = aClientThatHasOpenedTheirLink(token);
        String version = consentVersionShownTo(theirBrowser, token);
        accept(theirBrowser, token, COSMIN_NAME, PASSWORD, version);

        String second = anInvitationSentTo(IOANA);
        jdbc.update("update workspace_invitation set email = ? where email = ?", COSMIN, IOANA);
        RoundTripClient again = aClientThatHasOpenedTheirLink(second);

        ResponseEntity<String> refused = accept(again, second, "Cosmin Again", PASSWORD, version);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("ALREADY_MEMBER");
    }

    @Test
    void aConsentVersionThatIsNoLongerTheCurrentOneIsRefused() throws Exception {
        String token = anInvitationSentTo(COSMIN);
        RoundTripClient theirBrowser = aClientThatHasOpenedTheirLink(token);

        ResponseEntity<String> refused = accept(theirBrowser, token, COSMIN_NAME, PASSWORD, "000000000000");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("CONSENT_VERSION_STALE");
        assertThat(accountCountFor(COSMIN)).isZero();
        assertThat(consentRecordCount()).isZero();
    }

    @Test
    void acceptingWithoutSayingSoIsRefusedAndWritesNoConsentRecord() throws Exception {
        String token = anInvitationSentTo(COSMIN);
        RoundTripClient theirBrowser = aClientThatHasOpenedTheirLink(token);
        String version = consentVersionShownTo(theirBrowser, token);

        ResponseEntity<String> refused = theirBrowser.post(
                "/api/workspace/invitations/" + token + "/accept",
                "{\"displayName\": \"Cosmin Ionescu\", \"password\": \"" + PASSWORD
                        + "\", \"consentAccepted\": false, \"consentVersion\": \"" + version + "\"}");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("CONSENT_REQUIRED");
        assertThat(accountCountFor(COSMIN)).isZero();
        assertThat(consentRecordCount()).isZero();
    }

    @Test
    void decliningCreatesNothingAndKeepsOnlyTheOutcomeAndTheTime() throws Exception {
        String token = anInvitationSentTo(COSMIN);
        RoundTripClient theirBrowser = aClientThatHasOpenedTheirLink(token);

        ResponseEntity<String> declined = theirBrowser.post("/api/workspace/invitations/" + token + "/decline", "");

        assertThat(declined.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(storedStateOf(COSMIN)).isEqualTo("DECLINED");
        assertThat(declinedMomentOf(COSMIN))
                .as("the reconsideration window is measured from it")
                .isNotNull();
        assertThat(accountCountFor(COSMIN)).isZero();
        assertThat(membershipCountFor(COSMIN)).isZero();
        assertThat(consentRecordCount()).isZero();
        assertThat(recordedActions())
                .as("the decline is recorded, in order, and nothing claims somebody joined")
                .containsSubsequence("INVITATION_CREATED", "INVITATION_DECLINED")
                .doesNotContain("INVITATION_ACCEPTED");
    }

    @Test
    void aDeclinedInvitationCannotThenBeAccepted() throws Exception {
        String token = anInvitationSentTo(COSMIN);
        RoundTripClient theirBrowser = aClientThatHasOpenedTheirLink(token);
        theirBrowser.post("/api/workspace/invitations/" + token + "/decline", "");

        ResponseEntity<String> refused = accept(theirBrowser, token, COSMIN_NAME, PASSWORD, "any");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("INVITATION_NOT_USABLE");
    }

    @Test
    void acceptingAndDecliningAreReachableWithoutASessionAndRevokingIsNot() throws Exception {
        String token = anInvitationSentTo(COSMIN);
        String invitationId =
                jdbc.queryForObject("select id::text from workspace_invitation where email = ?", String.class, COSMIN);
        RoundTripClient stranger = aClientThatHasOpenedTheirLink(token);

        ResponseEntity<String> revoke = stranger.post("/api/workspace/invitations/" + invitationId + "/revoke", "");
        assertThat(revoke.getStatusCode())
                .as("an anonymous caller must not be able to withdraw somebody's invitation")
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> decline = stranger.post("/api/workspace/invitations/" + token + "/decline", "");
        assertThat(decline.getStatusCode())
                .as("an invited person has no session, so this must not require one")
                .isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void neitherEventLogEverCarriesThePasswordTheTokenOrItsHash() throws Exception {
        String token = anInvitationSentTo(COSMIN);
        RoundTripClient theirBrowser = aClientThatHasOpenedTheirLink(token);
        String version = consentVersionShownTo(theirBrowser, token);
        accept(theirBrowser, token, COSMIN_NAME, PASSWORD, version);

        String tokenHash = jdbc.queryForObject(
                "select token_hash from workspace_invitation where email = ?", String.class, COSMIN);

        assertThat(everyValueIn("workspace_event")).isNotEmpty();
        assertThat(everyValueIn("auth_event")).isNotEmpty();

        for (String row : everyValueIn("workspace_event")) {
            assertThat(row).doesNotContain(PASSWORD).doesNotContain(token).doesNotContain(tokenHash);
        }
        for (String row : everyValueIn("auth_event")) {
            assertThat(row).doesNotContain(PASSWORD).doesNotContain(token).doesNotContain(tokenHash);
        }
    }

    private List<String> everyValueIn(String table) {
        return jdbc.query("select * from " + table, (rows, rowNumber) -> {
            StringBuilder row = new StringBuilder();
            for (int column = 1; column <= rows.getMetaData().getColumnCount(); column++) {
                row.append(rows.getString(column)).append(' ');
            }
            return row.toString();
        });
    }

    @Test
    void acceptingAndDecliningStillRequireTheCrossSiteToken() throws Exception {
        String token = anInvitationSentTo(COSMIN);
        RoundTripClient theirBrowser = aClientThatHasOpenedTheirLink(token);
        String version = consentVersionShownTo(theirBrowser, token);
        String body = "{\"displayName\": \"Cosmin Ionescu\", \"password\": \"" + PASSWORD
                + "\", \"consentAccepted\": true, \"consentVersion\": \"" + version + "\"}";

        ResponseEntity<String> withoutToken = theirBrowser.withoutCrossSiteToken(
                HttpMethod.POST, "/api/workspace/invitations/" + token + "/accept", body);
        ResponseEntity<String> declineWithoutToken = theirBrowser.withoutCrossSiteToken(
                HttpMethod.POST, "/api/workspace/invitations/" + token + "/decline", "");

        assertThat(withoutToken.getStatusCode().is2xxSuccessful())
                .as("a cross-site page must not be able to make somebody join")
                .isFalse();
        assertThat(declineWithoutToken.getStatusCode().is2xxSuccessful())
                .as("a cross-site page must not be able to discard an invitation")
                .isFalse();
        assertThat(accountCountFor(COSMIN)).isZero();
        assertThat(storedStateOf(COSMIN)).isEqualTo("SENT");

        assertThat(accept(theirBrowser, token, COSMIN_NAME, PASSWORD, version).getStatusCode())
                .as("the refusals above must be about the token and nothing else")
                .isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void aReaderAskingForALanguageWeDoNotShipGetsEnglishAndTheRecordSaysEnglish() throws Exception {
        String token = anInvitationSentTo(COSMIN);
        RoundTripClient theirBrowser = aClientThatHasOpenedTheirLink(token);
        String version = consentVersionShownTo(theirBrowser, token);

        accept(theirBrowser, token, COSMIN_NAME, PASSWORD, version);

        assertThat(jdbc.queryForObject("select language from workspace_consent_record", String.class))
                .as("a record naming a language whose words were never served is a false statement")
                .isEqualTo("en");
    }

    private RoundTripClient aClientThatHasOpenedTheirLink(String token) {
        RoundTripClient fresh = new RoundTripClient(rest);
        fresh.forget();
        fresh.get("/api/workspace/invitations/" + token);
        return fresh;
    }

    private ResponseEntity<String> accept(
            RoundTripClient client, String token, String name, String password, String version) {
        String body =
                """
                {"displayName": "%s", "password": "%s", "consentAccepted": true, "consentVersion": "%s"}
                """
                        .formatted(name, password, version);
        return client.post("/api/workspace/invitations/" + token + "/accept", body);
    }

    private String consentVersionShownTo(RoundTripClient client, String token) throws Exception {
        ResponseEntity<String> preview = client.get("/api/workspace/invitations/" + token);
        return json.readTree(preview.getBody()).get("consent").get("version").asText();
    }

    private String anInvitationSentTo(String email) throws Exception {
        if (accountCountFor(OWNER_EMAIL) == 0) {
            registerTheOwnerAndSetUp();
        }
        String ownerMembership = jdbc.queryForObject(
                "select m.id::text from workspace_membership m join auth_user u on u.id = m.user_id"
                        + " where u.email = ?",
                String.class,
                OWNER_EMAIL);
        browser.post(
                "/api/workspace/invitations",
                """
                {"emailAddress": "%s", "role": "EMPLOYEE", "managerId": "%s"}
                """
                        .formatted(email, ownerMembership));
        return tokenFromTheLastMessage();
    }

    private void registerTheOwnerAndSetUp() throws Exception {
        browser.get("/api/auth/session");
        browser.post("/api/auth/signup/passcode", "{\"email\": \"" + OWNER_EMAIL + "\"}");
        browser.post(
                "/api/auth/signup",
                """
                {"email": "%s", "passcode": "%s", "password": "%s"}
                """
                        .formatted(OWNER_EMAIL, passcodeFromTheLastMessage(), PASSWORD));
        browser.post(
                "/api/workspace/setup",
                """
                {"ownerName": "Maria Enache", "workspaceName": "Atelier București",
                 "use": "WORK", "timezone": "Europe/Bucharest"}
                """);
    }

    private String passcodeFromTheLastMessage() {
        Matcher code = PASSCODE_IN_BODY.matcher(lastMessageBody());
        if (!code.find()) {
            throw new AssertionError("no passcode was delivered");
        }
        return code.group(1);
    }

    private String tokenFromTheLastMessage() {
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeastOnce()).send(sent.capture());
        String body = sent.getAllValues().getLast().getText();
        Matcher token = TOKEN_IN_BODY.matcher(body == null ? "" : body);
        if (!token.find()) {
            throw new AssertionError("no invitation link was delivered");
        }
        return token.group(1);
    }

    private String lastMessageBody() {
        ArgumentCaptor<String> delivered = ArgumentCaptor.forClass(String.class);
        verify(mailDispatcher, atLeastOnce()).send(any(EmailAddress.class), anyString(), delivered.capture());
        return delivered.getAllValues().getLast();
    }

    private int accountCountFor(String email) {
        return jdbc.queryForObject("select count(*) from auth_user where email = ?", Integer.class, email);
    }

    private int credentialCount() {
        return jdbc.queryForObject("select count(*) from auth_credential", Integer.class) - 1;
    }

    private String storedDisplayNameOf(String email) {
        return jdbc.queryForObject("select display_name from auth_user where email = ?", String.class, email);
    }

    private String storedStateOf(String email) {
        return jdbc.queryForObject("select state from workspace_invitation where email = ?", String.class, email);
    }

    private Object declinedMomentOf(String email) {
        return jdbc.queryForObject("select declined_at from workspace_invitation where email = ?", Object.class, email);
    }

    private int membershipCountFor(String email) {
        return jdbc.queryForObject(
                "select count(*) from workspace_membership m join auth_user u on u.id = m.user_id"
                        + " where u.email = ?",
                Integer.class,
                email);
    }

    private String managerOfTheMembershipFor(String email) {
        return jdbc.queryForObject(
                "select m.manager_id::text from workspace_membership m join auth_user u on u.id = m.user_id"
                        + " where u.email = ?",
                String.class,
                email);
    }

    private String ownerMembershipId() {
        return jdbc.queryForObject(
                "select m.id::text from workspace_membership m join auth_user u on u.id = m.user_id"
                        + " where u.email = ?",
                String.class,
                OWNER_EMAIL);
    }

    private int consentRecordCount() {
        return jdbc.queryForObject("select count(*) from workspace_consent_record", Integer.class);
    }

    private List<String> recordedActions() {
        return jdbc.queryForList("select action from workspace_event order by occurred_at", String.class);
    }
}
