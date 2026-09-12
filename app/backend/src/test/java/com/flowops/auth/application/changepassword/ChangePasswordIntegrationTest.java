package com.flowops.auth.application.changepassword;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.flowops.auth.AuthIntegrationTest;
import com.flowops.auth.domain.enums.AuthAction;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

@Tag("AUTH-CHANGE-PASSWORD-01")
class ChangePasswordIntegrationTest extends AuthIntegrationTest {
    private static final String EMAIL = "founder@flowops.test";
    private static final String NEW_PASSWORD = "an-even-longer-passphrase";

    @Test
    void theNewPasswordWorksAndTheOldOneDoesNot() throws Exception {
        Cookie session = elevatedSession();

        assertThat(changePassword(session, NEW_PASSWORD).getStatus()).isEqualTo(204);

        assertThat(logIn(EMAIL, NEW_PASSWORD).getStatus()).isEqualTo(200);
        assertThat(logIn(EMAIL, VALID_PASSWORD).getStatus()).isEqualTo(401);
    }

    @Test
    void neitherPasswordIsStoredInClear() throws Exception {
        Cookie session = elevatedSession();

        changePassword(session, NEW_PASSWORD);

        assertThat(credentials.findAll()).allSatisfy(credential -> assertThat(credential.getPasswordHash())
                .doesNotContain(NEW_PASSWORD)
                .doesNotContain(VALID_PASSWORD));
    }

    @Test
    void theOtherSessionsStopWorking() throws Exception {
        Cookie other = sessionCookieOf(signUp(EMAIL, VALID_PASSWORD));
        Cookie mine = sessionCookieOf(logIn(EMAIL, VALID_PASSWORD));
        reauthenticate(mine, VALID_PASSWORD);

        changePassword(mine, NEW_PASSWORD);

        mockMvc.perform(get("/api/auth/session").cookie(other))
                .andExpect(
                        result -> assertThat(result.getResponse().getStatus()).isEqualTo(401));
    }

    @Test
    void replayingTheIdentifierHeldBeforeTheChangeIsUnauthenticated() throws Exception {
        Cookie beforeTheChange = sessionCookieOf(signUp(EMAIL, VALID_PASSWORD));
        reauthenticate(beforeTheChange, VALID_PASSWORD);

        MockHttpServletResponse changed = changePassword(beforeTheChange, NEW_PASSWORD);

        assertThat(changed.getStatus()).isEqualTo(204);
        mockMvc.perform(get("/api/auth/session").cookie(beforeTheChange))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("the pre-change identifier must authenticate nothing")
                        .isEqualTo(401));
    }

    @Test
    void thePersonStaysSignedInUnderANewIdentifier() throws Exception {
        Cookie beforeTheChange = sessionCookieOf(signUp(EMAIL, VALID_PASSWORD));
        reauthenticate(beforeTheChange, VALID_PASSWORD);

        Cookie afterTheChange = sessionCookieOf(changePassword(beforeTheChange, NEW_PASSWORD));

        assertThat(afterTheChange).as("a fresh session cookie must be issued").isNotNull();
        assertThat(sessionIdOf(afterTheChange)).isNotEqualTo(sessionIdOf(beforeTheChange));
        mockMvc.perform(get("/api/auth/session").cookie(afterTheChange))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("the person carries on without signing in again")
                        .isEqualTo(200));
    }

    @Test
    void theElevationDoesNotCarryToTheNewIdentifier() throws Exception {
        Cookie beforeTheChange = sessionCookieOf(signUp(EMAIL, VALID_PASSWORD));
        reauthenticate(beforeTheChange, VALID_PASSWORD);

        Cookie afterTheChange = sessionCookieOf(changePassword(beforeTheChange, NEW_PASSWORD));

        assertThat(changePassword(afterTheChange, "a third long enough password")
                        .getStatus())
                .as("the next sensitive action must challenge again")
                .isEqualTo(403);
    }

    @Test
    void theCulledSessionsTakeTheirPersonalMetadataWithThem() throws Exception {
        sessionCookieOf(signUp(EMAIL, VALID_PASSWORD));
        Cookie mine = sessionCookieOf(logIn(EMAIL, VALID_PASSWORD));
        reauthenticate(mine, VALID_PASSWORD);
        assertThat(sessionMetadata.count()).isEqualTo(2);

        changePassword(mine, NEW_PASSWORD);

        assertThat(sessionMetadata.count()).isEqualTo(1);
    }

    @Test
    void metadataOrphanedByAnExpiredSessionIsCollectedToo() throws Exception {
        Cookie expired = sessionCookieOf(signUp(EMAIL, VALID_PASSWORD));
        Cookie mine = sessionCookieOf(logIn(EMAIL, VALID_PASSWORD));
        reauthenticate(mine, VALID_PASSWORD);
        jdbc.update("delete from spring_session where session_id = ?", sessionIdOf(expired));
        assertThat(sessionMetadata.count()).isEqualTo(2);

        changePassword(mine, NEW_PASSWORD);

        assertThat(sessionMetadata.count())
                .as("only the caller's own metadata may remain")
                .isEqualTo(1);
    }

    @Test
    void theChangeIsRecorded() throws Exception {
        Cookie session = elevatedSession();

        changePassword(session, NEW_PASSWORD);

        assertThat(recordedActions()).contains(AuthAction.PASSWORD_CHANGED.name());
    }

    @Test
    void aSessionOutsideItsWindowIsChallengedAndNothingChanges() throws Exception {
        Cookie session = sessionCookieOf(signUp(EMAIL, VALID_PASSWORD));

        MockHttpServletResponse response = changePassword(session, NEW_PASSWORD);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("REAUTHENTICATION_REQUIRED");
        assertThat(logIn(EMAIL, VALID_PASSWORD).getStatus()).isEqualTo(200);
        assertThat(recordedActions()).doesNotContain(AuthAction.PASSWORD_CHANGED.name());
    }

    @Test
    void aPasswordFailingThePolicyIsRefusedNamingTheRuleAndChangesNothing() throws Exception {
        Cookie session = elevatedSession();

        MockHttpServletResponse response = changePassword(session, "short");

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("MINIMUM_LENGTH");
        assertThat(logIn(EMAIL, VALID_PASSWORD).getStatus()).isEqualTo(200);
    }

    @Test
    void theCurrentPasswordIsRefusedAsUnchanged() throws Exception {
        Cookie session = elevatedSession();

        MockHttpServletResponse response = changePassword(session, VALID_PASSWORD);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("NOT_CURRENT_PASSWORD");
    }

    @Test
    void noPasswordReachesTheEventRecord() throws Exception {
        Cookie session = elevatedSession();

        changePassword(session, NEW_PASSWORD);

        assertThat(everyRecordedEventValue())
                .noneMatch(row -> row.contains(NEW_PASSWORD) || row.contains(VALID_PASSWORD));
    }

    @Test
    void aFailureAfterTheChangeLeavesTheCredentialAndTheOtherSessionsUntouched() throws Exception {
        Cookie other = sessionCookieOf(signUp(EMAIL, VALID_PASSWORD));
        Cookie mine = sessionCookieOf(logIn(EMAIL, VALID_PASSWORD));
        reauthenticate(mine, VALID_PASSWORD);
        doThrow(new IllegalStateException("the event store is unavailable"))
                .when(appendAuthEventPort)
                .append(argThat(event -> event.action() == AuthAction.PASSWORD_CHANGED));

        changePassword(mine, NEW_PASSWORD);

        assertThat(logIn(EMAIL, VALID_PASSWORD).getStatus())
                .as("the old password must still work, because the change did not happen")
                .isEqualTo(200);
        assertThat(logIn(EMAIL, NEW_PASSWORD).getStatus())
                .as("the new password must not work")
                .isEqualTo(401);
        mockMvc.perform(get("/api/auth/session").cookie(other))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("the other session must survive a change that did not happen")
                        .isEqualTo(200));
    }

    private Cookie elevatedSession() throws Exception {
        Cookie session = sessionCookieOf(signUp(EMAIL, VALID_PASSWORD));
        reauthenticate(session, VALID_PASSWORD);
        return session;
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
