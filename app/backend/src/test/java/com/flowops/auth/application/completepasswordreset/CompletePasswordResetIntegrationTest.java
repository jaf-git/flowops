package com.flowops.auth.application.completepasswordreset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.flowops.auth.AuthIntegrationTest;
import com.flowops.auth.domain.enums.AuthAction;
import com.flowops.auth.domain.model.EmailAddress;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletResponse;

@Tag("AUTH-RESET-PASSWORD-01")
class CompletePasswordResetIntegrationTest extends AuthIntegrationTest {
    private static final String EMAIL = "maria@atelier.ro";
    private static final String OLD_PASSWORD = "a-long-enough-passphrase";
    private static final String NEW_PASSWORD = "corect-cal-baterie-capsator";
    private static final Pattern TOKEN_IN_LINK = Pattern.compile("token=(\\S+)");

    @Test
    void aFailureAtTheLastWriteLeavesThePasswordTheTokenAndTheSessionsUntouched() throws Exception {
        Cookie held = sessionCookieOf(signUp(EMAIL, OLD_PASSWORD));
        assertThat(sessionStatusOf(held))
                .as("the session is live before the attempt")
                .isEqualTo(200);

        String token = requestAResetAndReadTheLink();
        doThrow(new IllegalStateException("the event store is unavailable"))
                .when(appendAuthEventPort)
                .append(argThat(event -> event.action() == AuthAction.PASSWORD_RESET_COMPLETED));

        completeReset(token, NEW_PASSWORD);

        assertThat(logIn(EMAIL, OLD_PASSWORD).getStatus())
                .as("the old password must still work, because the reset did not happen")
                .isEqualTo(200);
        assertThat(logIn(EMAIL, NEW_PASSWORD).getStatus())
                .as("the new password must not work")
                .isEqualTo(401);
        assertThat(mockMvc.perform(get("/api/auth/password-reset/" + token))
                        .andReturn()
                        .getResponse()
                        .getStatus())
                .as("the token must not have been spent by a reset that did not happen")
                .isEqualTo(204);
        assertThat(sessionStatusOf(held))
                .as("a session must not be ended by a transaction that rolled back")
                .isEqualTo(200);
    }

    @Test
    void theSameFourWritesAllHappenWhenNothingFails() throws Exception {
        Cookie held = sessionCookieOf(signUp(EMAIL, OLD_PASSWORD));
        String token = requestAResetAndReadTheLink();

        completeReset(token, NEW_PASSWORD);

        assertThat(logIn(EMAIL, NEW_PASSWORD).getStatus()).isEqualTo(200);
        assertThat(logIn(EMAIL, OLD_PASSWORD).getStatus()).isEqualTo(401);
        assertThat(mockMvc.perform(get("/api/auth/password-reset/" + token))
                        .andReturn()
                        .getResponse()
                        .getStatus())
                .as("the token is spent once it has been used")
                .isEqualTo(410);
        assertThat(sessionStatusOf(held))
                .as("every session the account held is gone")
                .isEqualTo(401);
    }

    private int sessionStatusOf(Cookie session) throws Exception {
        return mockMvc.perform(get("/api/auth/sessions").cookie(session))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private MockHttpServletResponse completeReset(String token, String password) throws Exception {
        return mockMvc.perform(requestFor(
                        "/api/auth/password-reset/complete", Map.of("token", token, "newPassword", password)))
                .andReturn()
                .getResponse();
    }

    private String requestAResetAndReadTheLink() throws Exception {
        mockMvc.perform(requestFor("/api/auth/password-reset/request", Map.of("email", EMAIL)));

        ArgumentCaptor<String> delivered = ArgumentCaptor.forClass(String.class);
        verify(mailDispatcher, atLeastOnce()).send(any(EmailAddress.class), anyString(), delivered.capture());
        Matcher token = TOKEN_IN_LINK.matcher(delivered.getAllValues().getLast());
        if (!token.find()) {
            throw new AssertionError("the message that followed the request carried no reset link");
        }
        return token.group(1);
    }
}
