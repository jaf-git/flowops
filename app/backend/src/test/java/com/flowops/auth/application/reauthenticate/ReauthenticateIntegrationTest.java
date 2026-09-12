package com.flowops.auth.application.reauthenticate;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowops.auth.AuthIntegrationTest;
import com.flowops.auth.domain.enums.AuthAction;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

@Tag("AUTH-REAUTH-01")
class ReauthenticateIntegrationTest extends AuthIntegrationTest {
    private static final String EMAIL = "founder@flowops.test";

    @Test
    void aCorrectPasswordElevatesTheSession() throws Exception {
        Cookie session = sessionCookieOf(signUp(EMAIL, VALID_PASSWORD));

        MockHttpServletResponse response = reauthenticate(session, VALID_PASSWORD);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("reauthenticatedUntil");
        assertThat(recordedActions()).contains(AuthAction.REAUTHENTICATION_SUCCEEDED.name());
    }

    @Test
    void aWrongPasswordIsRefused() throws Exception {
        Cookie session = sessionCookieOf(signUp(EMAIL, VALID_PASSWORD));

        MockHttpServletResponse response = reauthenticate(session, "not the password");

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void aRefusedAttemptStillCommitsItsFailureEventAndItsCountedAttempt() throws Exception {
        Cookie session = sessionCookieOf(signUp(EMAIL, VALID_PASSWORD));
        long attemptsBefore = loginAttempts.count();

        reauthenticate(session, "not the password");

        assertThat(recordedActions()).contains(AuthAction.REAUTHENTICATION_FAILED.name());
        assertThat(loginAttempts.count()).isGreaterThan(attemptsBefore);
    }

    @Test
    void aCallerWithNoSessionIsRefusedBeforeTheUseCaseRuns() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(
                        requestFor("/api/auth/reauthenticate", Map.of("password", VALID_PASSWORD)))
                .andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString())
                .as("the chain refuses with a status and no body")
                .isEmpty();
        assertThat(recordedActions())
                .as("no use case ran, so nothing was recorded")
                .isEmpty();
    }

    @Test
    void theElevationDoesNotReachThePersonsOtherSessions() throws Exception {
        Cookie first = sessionCookieOf(signUp(EMAIL, VALID_PASSWORD));
        Cookie second = sessionCookieOf(logIn(EMAIL, VALID_PASSWORD));

        reauthenticate(first, VALID_PASSWORD);

        assertThat(changePassword(second, "a different long password").getStatus())
                .isEqualTo(403);
    }

    @Test
    void theElevationSurvivesIntoALaterRequest() throws Exception {
        Cookie session = sessionCookieOf(signUp(EMAIL, VALID_PASSWORD));

        reauthenticate(session, VALID_PASSWORD);

        assertThat(changePassword(session, "a different long password").getStatus())
                .isEqualTo(204);
    }

    @Test
    void noPasswordReachesTheEventRecord() throws Exception {
        Cookie session = sessionCookieOf(signUp(EMAIL, VALID_PASSWORD));

        reauthenticate(session, VALID_PASSWORD);
        reauthenticate(session, "not the password");

        assertThat(everyRecordedEventValue()).noneMatch(row -> row.contains(VALID_PASSWORD));
    }

    private MockHttpServletResponse reauthenticate(Cookie session, String password) throws Exception {
        return mockMvc.perform(requestFor("/api/auth/reauthenticate", Map.of("password", password))
                        .cookie(session))
                .andReturn()
                .getResponse();
    }

    private MockHttpServletResponse changePassword(Cookie session, String newPassword) throws Exception {
        return mockMvc.perform(requestFor("/api/auth/password", Map.of("newPassword", newPassword))
                        .cookie(session))
                .andReturn()
                .getResponse();
    }
}
