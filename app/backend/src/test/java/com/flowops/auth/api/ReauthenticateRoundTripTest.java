package com.flowops.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("AUTH-REAUTH-01")
class ReauthenticateRoundTripTest extends AuthRoundTripTest {
    @Test
    void confirmingThePasswordRaisesTheSessionForRealAndSaysUntilWhen() throws Exception {
        registerTheOwner();
        assertThat(browser.post("/api/auth/password", "{\"newPassword\":\"%s\"}".formatted(NEW_PASSWORD))
                        .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> response = reauthenticate(browser, PASSWORD);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(response).get("reauthenticatedUntil").asText()).isNotBlank();
        assertThat(browser.post("/api/auth/password", "{\"newPassword\":\"%s\"}".formatted(NEW_PASSWORD))
                        .getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void thereIsNothingToElevateWithoutASession() {
        browser.get("/api/auth/session");

        ResponseEntity<String> response = reauthenticate(browser, PASSWORD);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(browser.holdsSession()).isFalse();
    }

    @Test
    void aWrongPasswordIsRefusedAndTheSessionSurvives() {
        registerTheOwner();

        ResponseEntity<String> response = reauthenticate(browser, "not-the-right-passphrase");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(browser.get("/api/auth/session").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void aMissingPasswordIsRefusedWithTheSharedEnvelope() throws Exception {
        registerTheOwner();

        ResponseEntity<String> response = browser.post("/api/auth/reauthenticate", "{}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(bodyOf(response).get("code").asText()).isNotBlank();
    }
}
