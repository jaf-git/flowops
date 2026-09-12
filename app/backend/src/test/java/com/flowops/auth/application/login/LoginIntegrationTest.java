package com.flowops.auth.application.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowops.auth.AuthIntegrationTest;
import com.flowops.auth.domain.enums.AuthAction;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

@Tag("AUTH-LOGIN-01")
class LoginIntegrationTest extends AuthIntegrationTest {
    private static final String EMAIL = "founder@flowops.test";

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void aSuccessfulLoginOpensASessionAndCommitsItsEventAndItsMetadata() throws Exception {
        logOut(sessionCookieOf(signUp(EMAIL, VALID_PASSWORD)));
        events.deleteAll();

        MockHttpServletResponse response = logIn(EMAIL, VALID_PASSWORD);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(sessionCookieOf(response)).isNotNull();
        assertThat(recordedActions()).contains(AuthAction.LOGIN_SUCCEEDED.name());
        assertThat(sessionMetadata.findAll()).singleElement().satisfies(metadata -> {
            assertThat(metadata.getIpAddress()).isEqualTo(CLIENT_ADDRESS);
            assertThat(metadata.getDeviceSummary()).isNotBlank();
            assertThat(metadata.getUserId()).isNotNull();
        });
    }

    @Test
    void anUnknownAddressAndAWrongPasswordAreIndistinguishable() throws Exception {
        signUp(EMAIL, VALID_PASSWORD);

        MockHttpServletResponse wrongPassword = logIn(EMAIL, "not-the-right-passphrase");
        MockHttpServletResponse unknownAddress = logIn("nobody@flowops.test", VALID_PASSWORD);

        assertThat(wrongPassword.getStatus()).isEqualTo(401);
        assertThat(unknownAddress.getStatus()).isEqualTo(401);
        assertThat(wrongPassword.getContentAsString()).isEqualTo(unknownAddress.getContentAsString());
        assertThat(sessionCookieOf(wrongPassword)).isNull();
    }

    @Test
    void aRefusedLoginStillCommitsItsFailureEventAndItsCountedAttempt() throws Exception {
        signUp(EMAIL, VALID_PASSWORD);
        events.deleteAll();

        logIn(EMAIL, "not-the-right-passphrase");

        assertThat(recordedActions()).containsExactly(AuthAction.LOGIN_FAILED.name());
        assertThat(loginAttempts.countByPurposeAndSubjectKindAndSubjectAndAttemptedAtAfter(
                        "LOGIN", "EMAIL", EMAIL, Instant.now().minus(1, ChronoUnit.MINUTES)))
                .isEqualTo(1);
    }

    @Test
    void theSessionEndpointRefusesACallerWithoutASession() throws Exception {
        mockMvc.perform(get("/api/auth/session")).andExpect(status().isUnauthorized());
    }

    @Test
    void theFrameworkSuppliesNoAccountOfItsOwn() {
        assertThat(applicationContext.getBeanNamesForType(InMemoryUserDetailsManager.class))
                .isEmpty();
        assertThat(applicationContext.getBean(UserDetailsService.class))
                .isNotInstanceOf(InMemoryUserDetailsManager.class);
    }

    @Test
    void theSuppliedUserStoreAuthenticatesNobody() {
        UserDetailsService store = applicationContext.getBean(UserDetailsService.class);

        assertThatThrownBy(() -> store.loadUserByUsername(EMAIL)).isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void theSessionEndpointAnswersTheHolderOfTheCookieAndNoOneElse() throws Exception {
        MockHttpServletResponse signedUp = signUp(EMAIL, VALID_PASSWORD);

        mockMvc.perform(get("/api/auth/session").cookie(sessionCookieOf(signedUp)))
                .andExpect(status().isOk())
                .andExpect(result ->
                        assertThat(result.getResponse().getContentAsString()).contains(EMAIL));
    }

    @Test
    void loggingInDoesNotContinueUnderAnIdentifierThatExistedBeforehand() throws Exception {
        Cookie beforeLogin = sessionCookieOf(signUp(EMAIL, VALID_PASSWORD));
        logOut(beforeLogin);

        Cookie afterLogin = sessionCookieOf(
                mockMvc.perform(requestFor("/api/auth/login", Map.of("email", EMAIL, "password", VALID_PASSWORD))
                                .cookie(beforeLogin))
                        .andReturn()
                        .getResponse());

        assertThat(afterLogin)
                .as("authenticating must issue a cookie of its own")
                .isNotNull();
        assertThat(sessionIdOf(afterLogin))
                .as("the planted identifier must not become the authenticated one")
                .isNotEqualTo(sessionIdOf(beforeLogin));
        mockMvc.perform(get("/api/auth/session").cookie(beforeLogin)).andExpect(status().isUnauthorized());
    }
}
