package com.flowops.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.support.RoundTripClient;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("AUTH-TERMINATE-SESSION-01")
class TerminateSessionRoundTripTest extends AuthRoundTripTest {
    private String referenceOfTheOnlySessionOf(UUID person) {
        return jdbc.queryForObject(
                "select reference from auth_session_metadata where user_id = ?", String.class, person);
    }

    @Test
    void theOwnerEndsAnotherPersonsSessionAndItReallyEnds() {
        registerTheOwner();
        UUID owner = identityOf(OWNER_EMAIL);
        UUID employee = anEmployeeExists();
        RoundTripClient theirs = anotherBrowser();
        signIn(theirs, EMPLOYEE_EMAIL, PASSWORD);
        reauthenticate(browser, PASSWORD);

        ResponseEntity<String> response =
                browser.delete("/api/auth/sessions/%s".formatted(referenceOfTheOnlySessionOf(employee)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(theirs.get("/api/auth/session").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(recordedRowsFor("SESSION_TERMINATED")).singleElement().satisfies(row -> {
            assertThat(row.get("actor_user_id")).hasToString(owner.toString());
            assertThat(row.get("target_user_id")).hasToString(employee.toString());
        });
    }

    @Test
    void aReferenceThatNamesNoSessionSucceedsAndIsRecordedAsAnAttempt() {
        registerTheOwner();
        reauthenticate(browser, PASSWORD);

        ResponseEntity<String> response = browser.delete("/api/auth/sessions/%s".formatted(UUID.randomUUID()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(recordedActions()).contains("SESSION_TERMINATION_ATTEMPTED").doesNotContain("SESSION_TERMINATED");
    }

    @Test
    void anEmployeeMayNotEndSomebodyElsesSession() throws Exception {
        registerTheOwner();
        UUID owner = identityOf(OWNER_EMAIL);
        UUID employee = anEmployeeExists();
        RoundTripClient theirs = anotherBrowser();
        signIn(theirs, EMPLOYEE_EMAIL, PASSWORD);
        reauthenticate(theirs, PASSWORD);

        ResponseEntity<String> response =
                theirs.delete("/api/auth/sessions/%s".formatted(referenceOfTheOnlySessionOf(owner)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(bodyOf(response).get("code").asText()).isEqualTo("PERMISSION_DENIED");
        assertThat(browser.get("/api/auth/session").getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(recordedRowsFor("SESSION_TERMINATION_DENIED"))
                .singleElement()
                .satisfies(row -> assertThat(row.get("actor_user_id")).hasToString(employee.toString()));
    }

    @Test
    void anAnonymousCallerCannotEndAnything() {
        registerTheOwner();
        UUID owner = identityOf(OWNER_EMAIL);
        String reference = referenceOfTheOnlySessionOf(owner);
        RoundTripClient stranger = anotherBrowser();

        ResponseEntity<String> response = stranger.delete("/api/auth/sessions/%s".formatted(reference));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(browser.get("/api/auth/session").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void theOwnerMaySeeAnotherPersonsSessionsAndTheLookIsRecorded() throws Exception {
        registerTheOwner();
        UUID owner = identityOf(OWNER_EMAIL);
        UUID employee = anEmployeeExists();
        RoundTripClient theirs = anotherBrowser();
        signIn(theirs, EMPLOYEE_EMAIL, PASSWORD);

        ResponseEntity<String> response = browser.get("/api/auth/users/%s/sessions".formatted(employee));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(response)).isNotEmpty();
        assertThat(recordedRowsFor("SESSIONS_OF_PERSON_LISTED")).singleElement().satisfies(row -> {
            assertThat(row.get("actor_user_id")).hasToString(owner.toString());
            assertThat(row.get("target_user_id")).hasToString(employee.toString());
        });
    }

    @Test
    void anEmployeeMayNotSeeSomebodyElsesSessions() throws Exception {
        registerTheOwner();
        UUID owner = identityOf(OWNER_EMAIL);
        UUID employee = anEmployeeExists();
        RoundTripClient theirs = anotherBrowser();
        signIn(theirs, EMPLOYEE_EMAIL, PASSWORD);

        ResponseEntity<String> response = theirs.get("/api/auth/users/%s/sessions".formatted(owner));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(bodyOf(response).get("code").asText()).isEqualTo("PERMISSION_DENIED");
        assertThat(recordedRowsFor("SESSION_VIEW_DENIED")).singleElement().satisfies(row -> {
            assertThat(row.get("actor_user_id")).hasToString(employee.toString());
            assertThat(row.get("target_user_id")).hasToString(owner.toString());
        });
    }

    @Test
    void anAnonymousCallerMayNotListAnybodysSessions() {
        registerTheOwner();
        UUID owner = identityOf(OWNER_EMAIL);
        RoundTripClient stranger = anotherBrowser();

        ResponseEntity<String> response = stranger.get("/api/auth/users/%s/sessions".formatted(owner));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aMalformedIdentifierIsRefusedRatherThanTreatedAsAFault() throws Exception {
        registerTheOwner();

        ResponseEntity<String> response = browser.get("/api/auth/users/not-an-identifier/sessions");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(bodyOf(response).get("code").asText()).isEqualTo("REQUEST_INVALID");
    }
}
