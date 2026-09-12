package com.flowops.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("AUTH-LOGOUT-01")
class LogoutRoundTripTest extends AuthRoundTripTest {
    @Test
    void signingOutOverHttpEndsTheSession() {
        registerTheOwner();

        ResponseEntity<String> response = browser.post("/api/auth/logout", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(browser.get("/api/auth/session").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void loggingOutWithNoSessionAtAllStillSucceeds() {
        browser.get("/api/auth/session");

        ResponseEntity<String> response = browser.post("/api/auth/logout", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void loggingOutTwiceSucceedsBothTimes() {
        registerTheOwner();

        browser.post("/api/auth/logout", null);
        ResponseEntity<String> second = browser.post("/api/auth/logout", null);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void theSessionIsGoneFromStorageAndNotMerelyFromTheBrowser() {
        registerTheOwner();

        browser.post("/api/auth/logout", null);

        assertThat(jdbc.queryForObject("select count(*) from spring_session", Integer.class))
                .isZero();
    }
}
