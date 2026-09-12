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
import java.util.ArrayList;
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

@Tag("WORKSPACE-ERASE-PERSON-01")
class WorkspaceErasePersonRoundTripTest extends RoundTripTest {
    private static final String OWNER_EMAIL = "maria@atelier.ro";
    private static final String LEAVER_EMAIL = "ionut@atelier.ro";
    private static final String COLLEAGUE_EMAIL = "ioana@atelier.ro";
    private static final String LEAVER_NAME = "Ionuț Petrescu";
    private static final String COLLEAGUE_NAME = "Ioana Radu";
    private static final String PASSWORD = "a-long-enough-passphrase";
    private static final Pattern PASSCODE_IN_BODY = Pattern.compile("code is (\\S+)\\.");

    @Test
    void anAnonymousCallerCannotEraseAnybody() {
        ResponseEntity<String> refused =
                browser.post("/api/workspace/people/" + UUID.randomUUID() + "/erase", "{\"typedName\":\"anybody\"}");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void somebodyWithoutThePermissionIsRefusedAndTold() throws Exception {
        registerTheOwnerAndSetUp();
        String leaver = seedPerson(LEAVER_EMAIL, LEAVER_NAME, "MANAGER", rootMembership());
        seedPerson(COLLEAGUE_EMAIL, COLLEAGUE_NAME, "EMPLOYEE", rootMembership());
        deactivate(leaver);

        RoundTripClient herBrowser = signedInAs(COLLEAGUE_EMAIL);
        ResponseEntity<String> refused =
                herBrowser.post("/api/workspace/people/" + leaver + "/erase", typed(LEAVER_NAME));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(codeIn(refused)).isEqualTo("NOT_PERMITTED");
        assertThat(statusOf(leaver)).isEqualTo("DEACTIVATED");
    }

    @Test
    void theOwnerErasesADeactivatedPersonAndNothingIdentifyingIsLeft() throws Exception {
        registerTheOwnerAndSetUp();
        String leaver = seedPerson(LEAVER_EMAIL, LEAVER_NAME, "MANAGER", rootMembership());
        String leaverAccount = accountOf(leaver);
        deactivate(leaver);
        reauthenticate();

        ResponseEntity<String> done = erase(leaver, LEAVER_NAME);

        assertThat(done.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = json.readTree(done.getBody());
        assertThat(body.get("changed").asBoolean()).isTrue();
        assertThat(body.get("opaqueIdentifier").asText())
                .as("the stable identifier is the account's own, which every authored reference resolves through")
                .isEqualTo(leaverAccount);

        assertThat(statusOf(leaver)).isEqualTo("ERASED");
        assertThat(jdbc.queryForObject(
                        "select erased_at is not null from workspace_membership where id = ?::uuid",
                        Boolean.class,
                        leaver))
                .isTrue();
        assertThat(jdbc.queryForObject(
                        "select deactivated_at is not null from workspace_membership where id = ?::uuid",
                        Boolean.class,
                        leaver))
                .as("when access ended survives the erasure; it answers a different question from when identity did")
                .isTrue();
        assertThat(jdbc.queryForObject(
                        "select count(*) from auth_credential where user_id = ?::uuid", Integer.class, leaverAccount))
                .as("no password ever reaches this account again")
                .isZero();
        assertThat(eventCount("PERSON_ERASED")).isEqualTo(1);
    }

    @Test
    void afterErasureNoColumnAnywhereInTheDatabaseHoldsTheirNameOrTheirAddress() throws Exception {
        registerTheOwnerAndSetUp();

        String leaver = invitePersonAndAccept(LEAVER_EMAIL, LEAVER_NAME);

        RoundTripClient theirBrowser = new RoundTripClient(rest);
        theirBrowser.get("/api/auth/session");
        theirBrowser.post("/api/auth/login", "{\"email\":\"" + LEAVER_EMAIL + "\",\"password\":\"wrong-one\"}");
        assertThat(everyTextColumnHolding(LEAVER_EMAIL))
                .as("the fixture is only a fixture if the address is genuinely somewhere before we erase it")
                .isNotEmpty();

        deactivate(leaver);
        reauthenticate();
        assertThat(erase(leaver, LEAVER_NAME).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(everyTextColumnHolding(LEAVER_EMAIL)).isEmpty();
        assertThat(everyTextColumnHolding(LEAVER_NAME)).isEmpty();
    }

    @Test
    void theConsentRecordSurvivesWithItsVersionAndDateAndNamesNobody() throws Exception {
        registerTheOwnerAndSetUp();
        String leaver = seedPerson(LEAVER_EMAIL, LEAVER_NAME, "MANAGER", rootMembership());
        String leaverAccount = accountOf(leaver);
        jdbc.update(
                "insert into workspace_consent_record (id, user_id, language, version, consent_text, agreed_at)"
                        + " values (?, ?::uuid, 'ro', 'v1', 'Textul acordului.', ?)",
                UUID.randomUUID(),
                leaverAccount,
                OffsetDateTime.now());

        deactivate(leaver);
        reauthenticate();
        assertThat(erase(leaver, LEAVER_NAME).getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> consent = jdbc.queryForMap(
                "select version, agreed_at, language from workspace_consent_record where user_id = ?::uuid",
                leaverAccount);
        assertThat(consent.get("version")).isEqualTo("v1");
        assertThat(consent.get("agreed_at")).isNotNull();
    }

    @Test
    void somebodyStillWorkingHereCannotBeErased() throws Exception {
        registerTheOwnerAndSetUp();
        String stillHere = seedPerson(LEAVER_EMAIL, LEAVER_NAME, "MANAGER", rootMembership());
        reauthenticate();

        ResponseEntity<String> refused = erase(stillHere, LEAVER_NAME);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(codeIn(refused)).isEqualTo("SUBJECT_ACTIVE");
        assertThat(statusOf(stillHere)).isEqualTo("ACTIVE");
        assertThat(everyTextColumnHolding(LEAVER_EMAIL)).isNotEmpty();
    }

    @Test
    void aSessionThatHasNotBeenReauthenticatedIsChallengedAndDestroysNothing() throws Exception {
        registerTheOwnerAndSetUp();
        String leaver = seedPerson(LEAVER_EMAIL, LEAVER_NAME, "MANAGER", rootMembership());
        deactivate(leaver);

        ResponseEntity<String> challenged = erase(leaver, LEAVER_NAME);

        assertThat(challenged.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(codeIn(challenged))
                .as("a challenge is not a refusal; the caller is being told what to do next")
                .isEqualTo("REAUTHENTICATION_REQUIRED");
        assertThat(statusOf(leaver)).isEqualTo("DEACTIVATED");
        assertThat(everyTextColumnHolding(LEAVER_EMAIL)).isNotEmpty();
    }

    @Test
    void typingAColleaguesNameInsteadDestroysNothing() throws Exception {
        registerTheOwnerAndSetUp();
        String leaver = seedPerson(LEAVER_EMAIL, LEAVER_NAME, "MANAGER", rootMembership());
        seedPerson(COLLEAGUE_EMAIL, COLLEAGUE_NAME, "EMPLOYEE", rootMembership());
        deactivate(leaver);
        reauthenticate();

        ResponseEntity<String> refused = erase(leaver, COLLEAGUE_NAME);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(codeIn(refused)).isEqualTo("NAME_MISMATCH");
        assertThat(refused.getBody())
                .as("the refusal never answers with the name it is asking for, or the barrier is a formality")
                .doesNotContain(LEAVER_NAME);
        assertThat(statusOf(leaver)).isEqualTo("DEACTIVATED");
        assertThat(everyTextColumnHolding(LEAVER_EMAIL)).isNotEmpty();
    }

    @Test
    void erasingSomebodyTwiceChangesNothingTheSecondTimeAndRecordsItOnce() throws Exception {
        registerTheOwnerAndSetUp();
        String leaver = seedPerson(LEAVER_EMAIL, LEAVER_NAME, "MANAGER", rootMembership());
        deactivate(leaver);
        reauthenticate();
        assertThat(erase(leaver, LEAVER_NAME).getStatusCode()).isEqualTo(HttpStatus.OK);
        String erasedAt = jdbc.queryForObject(
                "select erased_at::text from workspace_membership where id = ?::uuid", String.class, leaver);

        ResponseEntity<String> again = erase(leaver, "anything at all");

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(again.getBody()).get("changed").asBoolean())
                .as("the caller is told nothing happened, which the status alone cannot say")
                .isFalse();
        assertThat(eventCount("PERSON_ERASED")).as("one act, one record").isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select erased_at::text from workspace_membership where id = ?::uuid", String.class, leaver))
                .as("the moment identity was destroyed is not rewritten by asking again")
                .isEqualTo(erasedAt);
    }

    @Test
    void anErasedPersonIsInTheDirectoryUnderNoFilterAtAll() throws Exception {
        registerTheOwnerAndSetUp();
        String leaver = seedPerson(LEAVER_EMAIL, LEAVER_NAME, "MANAGER", rootMembership());
        deactivate(leaver);

        assertThat(browser.get("/api/workspace/people").getBody())
                .as("they are visible while merely deactivated, which is what makes the next assertion mean something")
                .contains(LEAVER_NAME);

        reauthenticate();
        assertThat(erase(leaver, LEAVER_NAME).getStatusCode()).isEqualTo(HttpStatus.OK);

        String directory = browser.get("/api/workspace/people").getBody();
        assertThat(directory).doesNotContain(LEAVER_NAME);
        assertThat(directory).doesNotContain(leaver);
    }

    @Test
    void anErasedMembershipIsExcludedByItsStatusAndNotMerelyByHavingNoName() throws Exception {
        registerTheOwnerAndSetUp();
        String ghost = seedPerson(LEAVER_EMAIL, LEAVER_NAME, "MANAGER", rootMembership());
        jdbc.update("update workspace_membership set status = 'ERASED', erased_at = now() where id = ?::uuid", ghost);

        String directory = browser.get("/api/workspace/people").getBody();

        assertThat(jdbc.queryForObject(
                        "select display_name from auth_user where id = ?::uuid", String.class, accountOf(ghost)))
                .as("the account still has a name, which is what makes the status the only thing hiding them")
                .isEqualTo(LEAVER_NAME);
        assertThat(directory).doesNotContain(LEAVER_NAME);
    }

    @Test
    void thePreviewNamesTheBarrierForSomebodyStillWorkingHere() throws Exception {
        registerTheOwnerAndSetUp();
        String stillHere = seedPerson(LEAVER_EMAIL, LEAVER_NAME, "MANAGER", rootMembership());

        JsonNode preview = json.readTree(
                browser.get("/api/workspace/people/" + stillHere + "/erasure").getBody());

        assertThat(preview.get("eligible").asBoolean()).isFalse();
        assertThat(preview.get("refusal").asText()).isEqualTo("SUBJECT_ACTIVE");
        assertThat(preview.get("displayName").asText()).isEqualTo(LEAVER_NAME);
        assertThat(preview.get("destroys")).isNotEmpty();
        assertThat(preview.get("survives")).isNotEmpty();
        assertThat(preview.toString())
                .as("no address in this response; the directory forbids exactly that shape and so does this")
                .doesNotContain(LEAVER_EMAIL);
    }

    private List<String> everyTextColumnHolding(String value) {
        List<Map<String, Object>> columns = jdbc.queryForList(
                """
                select table_name, column_name
                from information_schema.columns
                where table_schema = 'public'
                  and data_type in ('character varying', 'text', 'character')
                """);

        List<String> found = new ArrayList<>();
        for (Map<String, Object> column : columns) {
            String table = (String) column.get("table_name");
            String field = (String) column.get("column_name");
            Integer hits = jdbc.queryForObject(
                    "select count(*) from %s where %s = ?".formatted(table, field), Integer.class, value);
            if (hits != null && hits > 0) {
                found.add(table + "." + field);
            }
        }
        return found;
    }

    private String invitePersonAndAccept(String email, String displayName) throws Exception {
        assertThat(browser.post(
                                "/api/workspace/invitations",
                                "{\"emailAddress\":\"%s\",\"role\":\"MANAGER\",\"managerId\":\"%s\"}"
                                        .formatted(email, rootMembership()))
                        .getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        String token = jdbc.queryForObject(
                "select token_hash from workspace_invitation where lower(email) = lower(?)", String.class, email);
        assertThat(token).as("the invitation exists and carries the address").isNotNull();

        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(mailSender, atLeastOnce())
                .send(org.mockito.ArgumentMatchers.any(org.springframework.mail.SimpleMailMessage.class));

        String clearToken = invitationTokenFromTheMessage();

        RoundTripClient theirBrowser = new RoundTripClient(rest);
        theirBrowser.get("/api/auth/session");
        String version = json.readTree(theirBrowser
                        .get("/api/workspace/invitations/" + clearToken)
                        .getBody())
                .get("consent")
                .get("version")
                .asText();
        assertThat(theirBrowser
                        .post(
                                "/api/workspace/invitations/" + clearToken + "/accept",
                                ("{\"displayName\":\"%s\",\"password\":\"%s\","
                                                + "\"consentAccepted\":true,\"consentVersion\":\"%s\"}")
                                        .formatted(displayName, PASSWORD, version))
                        .getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        return jdbc.queryForObject(
                "select m.id::text from workspace_membership m join auth_user u on u.id = m.user_id"
                        + " where lower(u.email) = lower(?)",
                String.class,
                email);
    }

    private String invitationTokenFromTheMessage() {
        ArgumentCaptor<org.springframework.mail.SimpleMailMessage> message =
                ArgumentCaptor.forClass(org.springframework.mail.SimpleMailMessage.class);
        verify(mailSender, atLeastOnce()).send(message.capture());
        String body = message.getAllValues().getLast().getText();
        java.util.regex.Matcher token =
                java.util.regex.Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(body == null ? "" : body);
        if (!token.find()) {
            throw new AssertionError("no invitation link was delivered");
        }
        return token.group(1);
    }

    private ResponseEntity<String> erase(String membershipId, String typedName) {
        return browser.post("/api/workspace/people/" + membershipId + "/erase", typed(typedName));
    }

    private ResponseEntity<String> deactivate(String membershipId) {
        ResponseEntity<String> done = browser.post("/api/workspace/people/" + membershipId + "/deactivate", "");
        assertThat(done.getStatusCode()).isEqualTo(HttpStatus.OK);
        return done;
    }

    private void reauthenticate() {
        assertThat(browser.post("/api/auth/reauthenticate", "{\"password\":\"%s\"}".formatted(PASSWORD))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    private static String typed(String name) {
        return "{\"typedName\":\"%s\"}".formatted(name);
    }

    private String codeIn(ResponseEntity<String> response) throws Exception {
        return json.readTree(response.getBody()).get("code").asText();
    }

    private String statusOf(String membershipId) {
        return jdbc.queryForObject(
                "select status from workspace_membership where id = ?::uuid", String.class, membershipId);
    }

    private String accountOf(String membershipId) {
        return jdbc.queryForObject(
                "select user_id::text from workspace_membership where id = ?::uuid", String.class, membershipId);
    }

    private String rootMembership() {
        return jdbc.queryForObject("select id::text from workspace_membership where manager_id is null", String.class);
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
