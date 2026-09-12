package com.flowops.auth.application.viewownsessions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.flowops.auth.AuthIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

@Tag("AUTH-VIEW-SESSIONS-01")
class ViewSessionsIntegrationTest extends AuthIntegrationTest {
    private static final String OWNER_EMAIL = "maria@atelier.ro";

    @Test
    void everySessionThePersonHoldsIsListed() throws Exception {
        sessionCookieOf(signUp(OWNER_EMAIL, VALID_PASSWORD));
        Cookie second = sessionCookieOf(logIn(OWNER_EMAIL, VALID_PASSWORD));

        assertThat(referencesIn(ownSessions(second))).hasSize(2);
    }

    @Test
    void exactlyOneOfThemIsMarkedCurrentAndItIsTheOneAsking() throws Exception {
        sessionCookieOf(signUp(OWNER_EMAIL, VALID_PASSWORD));
        Cookie second = sessionCookieOf(logIn(OWNER_EMAIL, VALID_PASSWORD));

        MockHttpServletResponse response = ownSessions(second);

        assertThat(countOf(response.getContentAsString(), "\"current\":true"))
                .as("one and only one may be marked")
                .isEqualTo(1);
        assertThat(referencesMarkedCurrentIn(response))
                .as("and it must be the session this request arrived on")
                .containsExactly(referenceOfSessionBehind(second));
    }

    @Test
    void theListIsOrderedMostRecentlyUsedFirst() throws Exception {
        Cookie older = sessionCookieOf(signUp(OWNER_EMAIL, VALID_PASSWORD));
        Cookie newer = sessionCookieOf(logIn(OWNER_EMAIL, VALID_PASSWORD));

        jdbc.update(
                "update spring_session set last_access_time = last_access_time - 60000 where session_id = ?",
                sessionIdOf(older));

        assertThat(referencesIn(ownSessions(newer)))
                .containsExactly(referenceOfSessionBehind(newer), referenceOfSessionBehind(older));
    }

    @Test
    void aPersonSignedInOnceSeesExactlyThatSessionMarkedCurrent() throws Exception {
        Cookie only = sessionCookieOf(signUp(OWNER_EMAIL, VALID_PASSWORD));

        String body = ownSessions(only).getContentAsString();

        assertThat(referencesIn(ownSessions(only))).hasSize(1);
        assertThat(countOf(body, "\"current\":true")).isEqualTo(1);
    }

    @Test
    void anotherPersonsSessionsNeverAppearInMyList() throws Exception {
        Cookie mine = sessionCookieOf(signUp(OWNER_EMAIL, VALID_PASSWORD));
        Cookie theirs = anEmployeeSignedIn();

        String body = ownSessions(mine).getContentAsString();

        assertThat(referencesIn(ownSessions(mine))).hasSize(1);
        assertThat(body)
                .as("no handle belonging to the other person may be in this response")
                .doesNotContain(referenceOfSessionBehind(theirs));
    }

    @Test
    void aSessionPastItsIdleExpiryIsNotListedAsActive() throws Exception {
        Cookie stale = sessionCookieOf(signUp(OWNER_EMAIL, VALID_PASSWORD));
        Cookie live = sessionCookieOf(logIn(OWNER_EMAIL, VALID_PASSWORD));
        jdbc.update("update spring_session set last_access_time = 0 where session_id = ?", sessionIdOf(stale));

        assertThat(referencesIn(ownSessions(live)))
                .as("only the session that is still live")
                .hasSize(1);
    }

    @Test
    void noSessionIdentifierIsAnywhereInTheResponse() throws Exception {
        Cookie first = sessionCookieOf(signUp(OWNER_EMAIL, VALID_PASSWORD));
        Cookie second = sessionCookieOf(logIn(OWNER_EMAIL, VALID_PASSWORD));

        String body = ownSessions(second).getContentAsString();

        assertThat(body).doesNotContain(sessionIdOf(first)).doesNotContain(sessionIdOf(second));
        assertThat(body)
                .as("nor the cookie value, which is the identifier encoded")
                .doesNotContain(first.getValue())
                .doesNotContain(second.getValue());
    }

    @Test
    void aCallerWithNoLiveSessionIsRefused() throws Exception {
        mockMvc.perform(get("/api/auth/sessions"))
                .andExpect(
                        result -> assertThat(result.getResponse().getStatus()).isEqualTo(401));
    }

    @Test
    void lookingAtOwnSessionsIsNotRecordedAsAnEvent() throws Exception {
        Cookie session = sessionCookieOf(signUp(OWNER_EMAIL, VALID_PASSWORD));
        int before = recordedActions().size();

        ownSessions(session);

        assertThat(recordedActions()).hasSize(before);
    }

    private MockHttpServletResponse ownSessions(Cookie session) throws Exception {
        return mockMvc.perform(get("/api/auth/sessions").cookie(session))
                .andReturn()
                .getResponse();
    }
}
