package com.flowops.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("AUTH-VIEW-SESSIONS-01")
class ViewSessionsRoundTripTest extends AuthRoundTripTest {
    @Test
    void aPersonSeesTheirOwnSessionsOverHttp() throws Exception {
        registerTheOwner();

        ResponseEntity<String> response = browser.get("/api/auth/sessions");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(response)).isNotEmpty();
        assertThat(bodyOf(response).get(0).get("current").asBoolean()).isTrue();
    }

    @Test
    void anAnonymousCallerIsRefusedTheList() {
        browser.get("/api/auth/session");

        assertThat(browser.get("/api/auth/sessions").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void theListPublishesTheOpaqueReferenceAndNeverTheSessionIdentifier() {
        registerTheOwner();
        String identifier = sessionIdentifierHeldBy(browser);

        ResponseEntity<String> response = browser.get("/api/auth/sessions");

        assertThat(identifier).isNotBlank();
        assertThat(response.getBody()).doesNotContain(identifier).doesNotContain(browser.cookie("SESSION"));
    }

    @Test
    void listingOnesOwnSessionsRecordsNothing() {
        registerTheOwner();

        browser.get("/api/auth/sessions");

        assertThat(recordedActions()).doesNotContain("SESSIONS_OF_PERSON_LISTED");
    }
}
