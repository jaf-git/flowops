package com.flowops.auth.application.logout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowops.auth.AuthIntegrationTest;
import com.flowops.auth.domain.enums.AuthAction;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

@Tag("AUTH-LOGOUT-01")
class LogoutIntegrationTest extends AuthIntegrationTest {
    private static final String EMAIL = "founder@flowops.test";

    @Test
    void loggingOutEndsTheSessionAndCommitsItsEvent() throws Exception {
        Cookie session = sessionCookieOf(signUp(EMAIL, VALID_PASSWORD));

        MockHttpServletResponse response = logOut(session);

        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(recordedActions()).contains(AuthAction.LOGGED_OUT.name());
    }

    @Test
    void theSessionAndItsPersonalMetadataAreBothGoneAfterwards() throws Exception {
        Cookie session = sessionCookieOf(signUp(EMAIL, VALID_PASSWORD));
        assertThat(sessionMetadata.count()).isEqualTo(1);

        logOut(session);

        assertThat(sessionMetadata.count()).isZero();
        mockMvc.perform(get("/api/auth/session").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void loggingOutTwiceSucceedsBothTimes() throws Exception {
        Cookie session = sessionCookieOf(signUp(EMAIL, VALID_PASSWORD));

        assertThat(logOut(session).getStatus()).isEqualTo(204);
        assertThat(logOut(session).getStatus()).isEqualTo(204);
    }

    @Test
    void loggingOutWithNoSessionAtAllSucceedsAndRecordsNothing() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(requestFor("/api/auth/logout", Map.of()))
                .andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(recordedActions()).isEmpty();
    }

    @Test
    void theAccountAndItsCredentialSurviveTheSessionEnding() throws Exception {
        Cookie session = sessionCookieOf(signUp(EMAIL, VALID_PASSWORD));

        logOut(session);

        assertThat(users.findByEmail(EMAIL)).isPresent();
        assertThat(credentials.count()).isEqualTo(1);
        assertThat(logIn(EMAIL, VALID_PASSWORD).getStatus()).isEqualTo(200);
    }
}
