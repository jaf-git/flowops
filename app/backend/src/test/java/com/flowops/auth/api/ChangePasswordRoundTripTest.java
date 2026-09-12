package com.flowops.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.support.RoundTripClient;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("AUTH-CHANGE-PASSWORD-01")
class ChangePasswordRoundTripTest extends AuthRoundTripTest {
    private ResponseEntity<String> changePasswordTo(String password) {
        return browser.post("/api/auth/password", "{\"newPassword\":\"%s\"}".formatted(password));
    }

    @Test
    void aSessionThatHasNotBeenReauthenticatedIsChallengedRatherThanAllowed() throws Exception {
        registerTheOwner();

        ResponseEntity<String> response = changePasswordTo(NEW_PASSWORD);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(bodyOf(response).get("code").asText()).isEqualTo("REAUTHENTICATION_REQUIRED");
    }

    @Test
    void confirmingThePasswordFirstLetsTheChangeThrough() {
        registerTheOwner();
        reauthenticate(browser, PASSWORD);

        ResponseEntity<String> response = changePasswordTo(NEW_PASSWORD);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void theNewPasswordIsTheOneThatWorksAfterwards() {
        registerTheOwner();
        reauthenticate(browser, PASSWORD);
        changePasswordTo(NEW_PASSWORD);
        browser.post("/api/auth/logout", null);

        assertThat(signIn(OWNER_EMAIL, NEW_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void theOldPasswordStopsWorking() {
        registerTheOwner();
        reauthenticate(browser, PASSWORD);
        changePasswordTo(NEW_PASSWORD);
        browser.post("/api/auth/logout", null);

        assertThat(signIn(OWNER_EMAIL, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void theOtherSessionEndsAndTheCallersOwnSurvives() {
        registerTheOwner();
        RoundTripClient elsewhere = anotherBrowser();
        assertThat(signIn(elsewhere, OWNER_EMAIL, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);

        reauthenticate(browser, PASSWORD);
        changePasswordTo(NEW_PASSWORD);

        assertThat(elsewhere.get("/api/auth/session").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(browser.get("/api/auth/session").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void theCallerKeepsTheirSeatUnderANewIdentifierAndTheOldOneIsDead() {
        registerTheOwner();
        String before = sessionIdentifierHeldBy(browser);
        reauthenticate(browser, PASSWORD);

        changePasswordTo(NEW_PASSWORD);

        String after = sessionIdentifierHeldBy(browser);
        assertThat(after).isNotEqualTo(before);
        assertThat(browser.get("/api/auth/session").getStatusCode()).isEqualTo(HttpStatus.OK);

        RoundTripClient replay = anotherBrowser();
        assertThat(replay.replaying(
                                "SESSION", Base64.getEncoder().encodeToString(before.getBytes(StandardCharsets.UTF_8)))
                        .get("/api/auth/session")
                        .getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void theElevationDoesNotCarryToTheNewIdentifier() {
        registerTheOwner();
        reauthenticate(browser, PASSWORD);
        changePasswordTo(NEW_PASSWORD);

        ResponseEntity<String> again = changePasswordTo("another-long-enough-passphrase");

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void thePasswordChangeIsRecorded() {
        registerTheOwner();
        reauthenticate(browser, PASSWORD);

        changePasswordTo(NEW_PASSWORD);

        assertThat(recordedActions()).contains("PASSWORD_CHANGED");
        assertThat(recordedRowsFor("PASSWORD_CHANGED"))
                .singleElement()
                .extracting(row -> row.get("actor_user_id").toString())
                .isEqualTo(identityOf(OWNER_EMAIL).toString());
    }

    @Test
    void anAnonymousCallerCannotChangeAPassword() {
        browser.get("/api/auth/session");

        assertThat(changePasswordTo(NEW_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aPasswordThatFailsThePolicyNamesTheRuleItBroke() throws Exception {
        registerTheOwner();
        reauthenticate(browser, PASSWORD);

        ResponseEntity<String> response = changePasswordTo("short");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(bodyOf(response).get("code").asText()).isEqualTo("PASSWORD_POLICY_VIOLATION");
        assertThat(bodyOf(response).get("details").toString()).contains("MINIMUM_LENGTH");
    }
}
