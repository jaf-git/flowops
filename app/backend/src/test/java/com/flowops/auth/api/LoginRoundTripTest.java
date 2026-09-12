package com.flowops.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("AUTH-LOGIN-01")
class LoginRoundTripTest extends AuthRoundTripTest {
    @Test
    void signingInOverHttpOpensASessionAndSaysWhereTheOwnerLands() throws Exception {
        registerTheOwner();
        browser.post("/api/auth/logout", null);

        ResponseEntity<String> response = signIn(OWNER_EMAIL, PASSWORD);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(response).get("landingTarget").asText()).isEqualTo("WORKSPACE_SETUP");
        assertThat(browser.holdsSession()).isTrue();
    }

    @Test
    void aWrongPasswordAndAnUnknownAddressAnswerIdentically() {
        registerTheOwner();
        browser.post("/api/auth/logout", null);

        ResponseEntity<String> wrongPassword = signIn(OWNER_EMAIL, "not-the-right-passphrase");
        ResponseEntity<String> unknownAddress = signIn("nobody@atelier.ro", PASSWORD);

        assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknownAddress.getStatusCode()).isEqualTo(wrongPassword.getStatusCode());
        assertThat(unknownAddress.getBody()).isEqualTo(wrongPassword.getBody());
    }

    @Test
    void aRefusedLoginLeavesNoSession() {
        registerTheOwner();
        browser.post("/api/auth/logout", null);

        signIn(OWNER_EMAIL, "not-the-right-passphrase");

        assertThat(browser.holdsSession()).isFalse();
        assertThat(browser.get("/api/auth/session").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void theSessionContextIsRefusedBeforeSigningInAndServedAfter() throws Exception {
        ResponseEntity<String> anonymous = browser.get("/api/auth/session");
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        registerTheOwner();
        ResponseEntity<String> signedIn = browser.get("/api/auth/session");

        assertThat(signedIn.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(signedIn).get("permissions")).isNotEmpty();
    }

    @Test
    void theSessionContextCarriesNoSessionIdentifier() {
        registerTheOwner();
        String sessionId = new String(Base64.getDecoder().decode(browser.cookie("SESSION")), StandardCharsets.UTF_8);

        ResponseEntity<String> context = browser.get("/api/auth/session");

        assertThat(sessionId).isNotBlank();
        assertThat(context.getBody()).doesNotContain(sessionId).doesNotContain(browser.cookie("SESSION"));
    }
}
