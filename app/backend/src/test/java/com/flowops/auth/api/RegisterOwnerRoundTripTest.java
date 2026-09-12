package com.flowops.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("AUTH-REGISTER-OWNER-01")
class RegisterOwnerRoundTripTest extends AuthRoundTripTest {
    @Test
    void anOwnerRegistersOverHttpAndEndsUpWithASession() throws Exception {
        registerTheOwner();

        ResponseEntity<String> context = browser.get("/api/auth/session");

        assertThat(context.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(context).get("email").asText()).isEqualTo(OWNER_EMAIL);
        assertThat(jdbc.queryForObject("select count(*) from auth_user", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void thePasscodeRequestIsAcceptedAndRevealsNothing() {
        browser.get("/api/auth/session");

        ResponseEntity<String> response =
                browser.post("/api/auth/signup/passcode", "{\"email\":\"" + OWNER_EMAIL + "\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void aSecondPersonIsToldTheInstallationIsClaimedAndStillCannotRegister() {
        registerTheOwner();

        ResponseEntity<String> asked =
                browser.post("/api/auth/signup/passcode", "{\"email\":\"" + EMPLOYEE_EMAIL + "\"}");

        assertThat(asked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject("select count(*) from auth_user", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void aMalformedAddressIsRefusedWithTheSharedEnvelope() throws Exception {
        browser.get("/api/auth/session");

        ResponseEntity<String> response = browser.post("/api/auth/signup/passcode", "{\"email\":\"not-an-address\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(bodyOf(response).get("code").asText()).isNotBlank();
    }

    @Test
    void registrationWithoutTheCrossSiteTokenIsRefusedAndWritesNothing() {
        browser.get("/api/auth/session");

        ResponseEntity<String> response = browser.withoutCrossSiteToken(
                HttpMethod.POST, "/api/auth/signup/passcode", "{\"email\":\"" + OWNER_EMAIL + "\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(jdbc.queryForObject("select count(*) from auth_signup_passcode", Integer.class))
                .isZero();
    }

    @Test
    void aWrongCrossSiteTokenIsRefusedAndWritesNothingEither() {
        browser.get("/api/auth/session");

        ResponseEntity<String> response = browser.withWrongCrossSiteToken(
                HttpMethod.POST, "/api/auth/signup/passcode", "{\"email\":\"" + OWNER_EMAIL + "\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(jdbc.queryForObject("select count(*) from auth_signup_passcode", Integer.class))
                .isZero();
    }
}
