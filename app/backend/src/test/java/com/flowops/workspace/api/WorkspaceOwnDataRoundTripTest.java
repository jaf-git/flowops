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

@Tag("WORKSPACE-VIEW-OWN-DATA-01")
class WorkspaceOwnDataRoundTripTest extends RoundTripTest {
    private static final String OWNER_EMAIL = "maria@atelier.ro";
    private static final String IONUT_EMAIL = "ionut@atelier.ro";
    private static final String IOANA_EMAIL = "ioana@atelier.ro";
    private static final String ANDREI_EMAIL = "andrei@atelier.ro";
    private static final String IONUT_NAME = "Ionuț Petrescu";
    private static final String IOANA_NAME = "Ioana Radu";
    private static final String ANDREI_NAME = "Andrei Munteanu";
    private static final String PASSWORD = "a-long-enough-passphrase";
    private static final Pattern PASSCODE_IN_BODY = Pattern.compile("code is (\\S+)\\.");

    @Test
    void anAnonymousCallerSeesNobodysData() {
        assertThat(browser.get("/api/workspace/me").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void anybodySeesTheirOwnAccountRoleReportingLineConsentAndSessions() throws Exception {
        registerTheOwnerAndSetUp();
        String ioana = seedPerson(IOANA_EMAIL, IOANA_NAME, "EMPLOYEE", rootMembership());
        giveConsent(accountOf(ioana));

        RoundTripClient herBrowser = signedInAs(IOANA_EMAIL);
        ResponseEntity<String> response = herBrowser.get("/api/workspace/me");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode file = json.readTree(response.getBody());
        assertThat(file.get("account").get("emailAddress").asText()).isEqualTo(IOANA_EMAIL);
        assertThat(file.get("account").get("displayName").asText()).isEqualTo(IOANA_NAME);
        assertThat(file.get("account").get("role").asText()).isEqualTo("EMPLOYEE");
        assertThat(file.get("account").get("sessions"))
                .as("she is reading this from a session, so there is at least one to show her")
                .isNotEmpty();
        assertThat(file.get("membership").get("managerName").asText()).isEqualTo("Maria Ionescu");
        assertThat(file.get("consent").get("version").asText()).isEqualTo("v1");
        assertThat(file.get("authored").get("tasks").asInt())
                .as("present and zero: nothing is authored yet, and an absent section reads as a refusal")
                .isZero();
    }

    @Test
    void onePersonsFileNamesNobodyOutsideTheirOwnReportingLine() throws Exception {
        registerTheOwnerAndSetUp();
        String ionut = seedPerson(IONUT_EMAIL, IONUT_NAME, "MANAGER", rootMembership());
        String ioana = seedPerson(IOANA_EMAIL, IOANA_NAME, "EMPLOYEE", ionut);
        seedPerson(ANDREI_EMAIL, ANDREI_NAME, "EMPLOYEE", ionut);
        giveConsent(accountOf(ioana));

        String file = signedInAs(IOANA_EMAIL).get("/api/workspace/me").getBody();

        assertThat(file)
                .as("her own manager is her data: the record of who could see her work")
                .contains(IONUT_NAME);
        assertThat(file).as("a colleague who is nothing to her").doesNotContain(ANDREI_NAME);
        assertThat(file).doesNotContain(ANDREI_EMAIL);
        assertThat(file).doesNotContain(IONUT_EMAIL);
        assertThat(file).as("the only address in her file is her own").contains(IOANA_EMAIL);
    }

    @Test
    void aReportingLineThatChangedTwiceShowsBothPeriods() throws Exception {
        String owner = registerTheOwnerAndSetUp();
        String ionut = seedPerson(IONUT_EMAIL, IONUT_NAME, "MANAGER", owner);
        String ioana = seedPerson(IOANA_EMAIL, IOANA_NAME, "EMPLOYEE", owner);

        assertThat(move(ioana, ionut).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(move(ioana, owner).getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode history = json.readTree(
                        signedInAs(IOANA_EMAIL).get("/api/workspace/me").getBody())
                .get("reportingLineHistory");

        assertThat(history).hasSize(3);
        assertThat(history.get(0).get("managerName").asText()).isEqualTo("Maria Ionescu");
        assertThat(history.get(1).get("managerName").asText()).isEqualTo(IONUT_NAME);
        assertThat(history.get(2).get("managerName").asText()).isEqualTo("Maria Ionescu");
        assertThat(history.get(2).get("until").isNull())
                .as("the period still in force has no end")
                .isTrue();
        assertThat(history.get(0).get("until").isNull()).isFalse();
    }

    @Test
    void theExportCarriesTheSameInformationAsTheScreenAndIsRecorded() throws Exception {
        registerTheOwnerAndSetUp();
        String ioana = seedPerson(IOANA_EMAIL, IOANA_NAME, "EMPLOYEE", rootMembership());

        RoundTripClient herBrowser = signedInAs(IOANA_EMAIL);
        String onScreen = herBrowser.get("/api/workspace/me").getBody();
        ResponseEntity<String> exported = herBrowser.post("/api/workspace/me/export", "");

        assertThat(exported.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exported.getHeaders().getFirst("Content-Disposition"))
                .as("a copy to keep, not a page to read")
                .contains("attachment");
        JsonNode file = json.readTree(exported.getBody());
        JsonNode screen = json.readTree(onScreen);
        assertThat(file.get("account")).isEqualTo(screen.get("account"));
        assertThat(file.get("membership")).isEqualTo(screen.get("membership"));

        assertThat(jdbc.queryForObject(
                        "select count(*) from workspace_data_export where subject_user_id = ?::uuid",
                        Integer.class,
                        accountOf(ioana)))
                .isEqualTo(1);
    }

    @Test
    void theSixthExportInADayIsRefusedAndTheFifthIsNot() throws Exception {
        registerTheOwnerAndSetUp();
        seedPerson(IOANA_EMAIL, IOANA_NAME, "EMPLOYEE", rootMembership());

        RoundTripClient herBrowser = signedInAs(IOANA_EMAIL);
        for (int copy = 1; copy <= 5; copy++) {
            assertThat(herBrowser.post("/api/workspace/me/export", "").getStatusCode())
                    .as("copy %d is within the bound", copy)
                    .isEqualTo(HttpStatus.OK);
        }

        ResponseEntity<String> refused = herBrowser.post("/api/workspace/me/export", "");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("EXPORT_LIMIT");
    }

    @Test
    void theOwnerProducesADeactivatedPersonsFileAndItSaysWhoMadeIt() throws Exception {
        registerTheOwnerAndSetUp();
        String ioana = seedPerson(IOANA_EMAIL, IOANA_NAME, "EMPLOYEE", rootMembership());
        assertThat(browser.post("/api/workspace/people/" + ioana + "/deactivate", "")
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<String> produced = browser.post("/api/workspace/people/" + ioana + "/export", "");

        assertThat(produced.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode file = json.readTree(produced.getBody());
        assertThat(file.get("account").get("emailAddress").asText()).isEqualTo(IOANA_EMAIL);
        assertThat(file.get("producedForSomebodyElse").asBoolean())
                .as("the person receiving this can see it was not made by them")
                .isTrue();
        assertThat(file.get("account").get("sessions"))
                .as("deactivation ended them, so empty is the honest answer rather than a missing one")
                .isEmpty();
        assertThat(jdbc.queryForObject(
                        "select produced_by_user_id <> subject_user_id from workspace_data_export"
                                + " where subject_user_id = ?::uuid",
                        Boolean.class,
                        accountOf(ioana)))
                .isTrue();
    }

    @Test
    void theOwnerCannotProduceTheFileOfSomebodyStillWorkingHere() throws Exception {
        registerTheOwnerAndSetUp();
        String ioana = seedPerson(IOANA_EMAIL, IOANA_NAME, "EMPLOYEE", rootMembership());

        ResponseEntity<String> refused = browser.post("/api/workspace/people/" + ioana + "/export", "");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("SUBJECT_ACTIVE");
        assertThat(jdbc.queryForObject("select count(*) from workspace_data_export", Integer.class))
                .as("a refusal records no copy, which would otherwise spend one of their five")
                .isZero();
    }

    @Test
    void anEmployeeCannotProduceAnybodyElsesFile() throws Exception {
        registerTheOwnerAndSetUp();
        String ionut = seedPerson(IONUT_EMAIL, IONUT_NAME, "MANAGER", rootMembership());
        seedPerson(IOANA_EMAIL, IOANA_NAME, "EMPLOYEE", rootMembership());

        ResponseEntity<String> refused = signedInAs(IOANA_EMAIL).post("/api/workspace/people/" + ionut + "/export", "");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("NOT_PERMITTED");
    }

    @Test
    void changingMyOwnNameStoresItAndRecordsThatItChangedWithoutRecordingEitherName() throws Exception {
        registerTheOwnerAndSetUp();
        String ioana = seedPerson(IOANA_EMAIL, IOANA_NAME, "EMPLOYEE", rootMembership());

        ResponseEntity<String> saved = signedInAs(IOANA_EMAIL)
                .exchange(
                        org.springframework.http.HttpMethod.PUT,
                        "/api/workspace/me/profile",
                        "{\"displayName\":\"Ioana Radu-Marin\"}");

        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(saved.getBody()).get("changed").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject(
                        "select display_name from auth_user where id = ?::uuid", String.class, accountOf(ioana)))
                .isEqualTo("Ioana Radu-Marin");
        assertThat(eventCount("PROFILE_CHANGED")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select count(*) from workspace_event where action = 'PROFILE_CHANGED'"
                                + " and subject_user_id = ?::uuid and actor_user_id = ?::uuid",
                        Integer.class,
                        accountOf(ioana),
                        accountOf(ioana)))
                .as("the person and the actor are the same by construction, and both are written")
                .isEqualTo(1);
    }

    @Test
    void theNewNameIsWhatEverybodyElseReadsImmediately() throws Exception {
        registerTheOwnerAndSetUp();
        seedPerson(IOANA_EMAIL, IOANA_NAME, "EMPLOYEE", rootMembership());

        assertThat(browser.get("/api/workspace/people").getBody()).contains(IOANA_NAME);
        signedInAs(IOANA_EMAIL)
                .exchange(
                        org.springframework.http.HttpMethod.PUT,
                        "/api/workspace/me/profile",
                        "{\"displayName\":\"Ioana Radu-Marin\"}");

        String directory = browser.get("/api/workspace/people").getBody();
        assertThat(directory).contains("Ioana Radu-Marin");
        assertThat(directory).doesNotContain(IOANA_NAME + "\"");
    }

    @Test
    void submittingTheNameTheyAlreadyHaveChangesNothingAndRecordsNothing() throws Exception {
        registerTheOwnerAndSetUp();
        seedPerson(IOANA_EMAIL, IOANA_NAME, "EMPLOYEE", rootMembership());

        RoundTripClient herBrowser = signedInAs(IOANA_EMAIL);
        herBrowser.exchange(
                org.springframework.http.HttpMethod.PUT,
                "/api/workspace/me/profile",
                "{\"displayName\":\"Ioana Marin\"}");
        ResponseEntity<String> again = herBrowser.exchange(
                org.springframework.http.HttpMethod.PUT,
                "/api/workspace/me/profile",
                "{\"displayName\":\"Ioana Marin\"}");

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(again.getBody()).get("changed").asBoolean()).isFalse();
        assertThat(eventCount("PROFILE_CHANGED"))
                .as("one real change, one record -- counted to one rather than to zero")
                .isEqualTo(1);
    }

    @Test
    void aNameOfNothingButSpacesIsRefused() throws Exception {
        registerTheOwnerAndSetUp();
        seedPerson(IOANA_EMAIL, IOANA_NAME, "EMPLOYEE", rootMembership());

        ResponseEntity<String> refused = signedInAs(IOANA_EMAIL)
                .exchange(
                        org.springframework.http.HttpMethod.PUT,
                        "/api/workspace/me/profile",
                        "{\"displayName\":\"   \"}");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(eventCount("PROFILE_CHANGED")).isZero();
    }

    private ResponseEntity<String> move(String membershipId, String proposedManagerId) {
        return browser.post(
                "/api/workspace/people/" + membershipId + "/manager",
                "{\"proposedManagerId\":\"%s\"}".formatted(proposedManagerId));
    }

    private void giveConsent(String account) {
        jdbc.update(
                "insert into workspace_consent_record (id, user_id, language, version, consent_text, agreed_at)"
                        + " values (?, ?::uuid, 'ro', 'v1', 'Textul acordului.', ?)",
                UUID.randomUUID(),
                account,
                OffsetDateTime.now());
    }

    private int eventCount(String action) {
        return jdbc.queryForObject("select count(*) from workspace_event where action = ?", Integer.class, action);
    }

    private String accountOf(String membershipId) {
        return jdbc.queryForObject(
                "select user_id::text from workspace_membership where id = ?::uuid", String.class, membershipId);
    }

    private String rootMembership() {
        return jdbc.queryForObject("select id::text from workspace_membership where manager_id is null", String.class);
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

        return rootMembership();
    }
}
