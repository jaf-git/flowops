package com.flowops.auth.application.terminatesession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.flowops.auth.AuthIntegrationTest;
import com.flowops.auth.domain.enums.AuthAction;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

@Tag("AUTH-TERMINATE-SESSION-01")
class TerminateSessionIntegrationTest extends AuthIntegrationTest {
    private static final String OWNER_EMAIL = "maria@atelier.ro";

    @Test
    void theOwnerEndsAnotherPersonsSessionAndItStopsWorking() throws Exception {
        Cookie owner = elevatedOwner();
        Cookie theirs = anEmployeeSignedIn();

        MockHttpServletResponse response = terminate(owner, referenceOfSessionBehind(theirs));

        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(statusOfSessionRead(theirs))
                .as("the person cut off must be unauthenticated on their next request")
                .isEqualTo(401);
    }

    @Test
    void theTerminationRecordsWhoEndedWhose() throws Exception {
        Cookie owner = elevatedOwner();
        Cookie theirs = anEmployeeSignedIn();

        terminate(owner, referenceOfSessionBehind(theirs));

        Map<String, Object> recorded = lastEventOf(AuthAction.SESSION_TERMINATED);
        assertThat(recorded.get("actor_user_id")).isEqualTo(ownerId(OWNER_EMAIL));
        assertThat(recorded.get("target_user_id")).isEqualTo(employeeId());
        assertThat(recorded.get("actor_user_id")).isNotEqualTo(recorded.get("target_user_id"));
    }

    @Test
    void theOwnersOwnSessionSurvives() throws Exception {
        Cookie owner = elevatedOwner();
        Cookie theirs = anEmployeeSignedIn();

        terminate(owner, referenceOfSessionBehind(theirs));

        assertThat(statusOfSessionRead(owner)).isEqualTo(200);
    }

    @Test
    void aNonOwnerIsRefusedAndNoSessionEnds() throws Exception {
        Cookie owner = elevatedOwner();
        Cookie employee = elevated(anEmployeeSignedIn());

        MockHttpServletResponse response = terminate(employee, referenceOfSessionBehind(owner));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString())
                .as("refused for the permission, not for a stale re-authentication window")
                .contains("PERMISSION_DENIED");
        assertThat(statusOfSessionRead(owner))
                .as("the session they tried to end must be untouched")
                .isEqualTo(200);
    }

    @Test
    void aRefusedTerminationIsRecordedWithActorAndTarget() throws Exception {
        Cookie owner = elevatedOwner();
        Cookie employee = elevated(anEmployeeSignedIn());

        terminate(employee, referenceOfSessionBehind(owner));

        Map<String, Object> recorded = lastEventOf(AuthAction.SESSION_TERMINATION_DENIED);
        assertThat(recorded.get("actor_user_id")).isEqualTo(employeeId());
        assertThat(recorded.get("target_user_id")).isEqualTo(ownerId(OWNER_EMAIL));
    }

    @Test
    void aNonOwnerCannotReadAnotherPersonsSessionsAndTheAttemptIsRecorded() throws Exception {
        elevatedOwner();
        Cookie employee = anEmployeeSignedIn();
        UUID owner = ownerId(OWNER_EMAIL);

        MockHttpServletResponse response = mockMvc.perform(
                        get("/api/auth/users/{userId}/sessions", owner).cookie(employee))
                .andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(lastEventOf(AuthAction.SESSION_VIEW_DENIED).get("target_user_id"))
                .isEqualTo(owner);
    }

    @Test
    void theOwnerCanReadAnotherPersonsSessions() throws Exception {
        Cookie owner = elevatedOwner();
        anEmployeeSignedIn();

        MockHttpServletResponse response = mockMvc.perform(
                        get("/api/auth/users/{userId}/sessions", employeeId()).cookie(owner))
                .andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(referencesIn(response)).hasSize(1);
    }

    @Test
    void terminatingAnAlreadyEndedSessionSucceeds() throws Exception {
        Cookie owner = elevatedOwner();
        Cookie theirs = anEmployeeSignedIn();
        String reference = referenceOfSessionBehind(theirs);
        terminate(owner, reference);

        assertThat(terminate(owner, reference).getStatus()).isEqualTo(204);
    }

    @Test
    void terminatingAnAlreadyEndedSessionRecordsNothingFurther() throws Exception {
        Cookie owner = elevatedOwner();
        Cookie theirs = anEmployeeSignedIn();
        String reference = referenceOfSessionBehind(theirs);
        terminate(owner, reference);

        terminate(owner, reference);

        assertThat(recordedActions().stream()
                        .filter(AuthAction.SESSION_TERMINATED.name()::equals)
                        .count())
                .isEqualTo(1);
    }

    @Test
    void aReferenceThatNeverExistedIsIndistinguishableFromOneThatEnded() throws Exception {
        Cookie owner = elevatedOwner();
        Cookie theirs = anEmployeeSignedIn();
        String ended = referenceOfSessionBehind(theirs);
        terminate(owner, ended);

        MockHttpServletResponse forEnded = terminate(owner, ended);
        MockHttpServletResponse forNeverExisted =
                terminate(owner, UUID.randomUUID().toString());

        assertThat(forNeverExisted.getStatus()).isEqualTo(forEnded.getStatus());
        assertThat(forNeverExisted.getContentAsString()).isEqualTo(forEnded.getContentAsString());
    }

    @Test
    void anOwnerOutsideTheReauthenticationWindowIsChallengedAndNothingEnds() throws Exception {
        Cookie owner = sessionCookieOf(signUp(OWNER_EMAIL, VALID_PASSWORD));
        Cookie theirs = anEmployeeSignedIn();

        MockHttpServletResponse response = terminate(owner, referenceOfSessionBehind(theirs));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("REAUTHENTICATION_REQUIRED");
        assertThat(statusOfSessionRead(theirs))
                .as("a challenged request must not have ended anything")
                .isEqualTo(200);
    }

    @Test
    void aMalformedReferenceIsARejectedRequestRatherThanAFault() throws Exception {
        Cookie owner = elevatedOwner();

        MockHttpServletResponse response = terminate(owner, "not-a-reference");

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("REQUEST_INVALID");
    }

    @Test
    void aCallerWithNoLiveSessionIsRefusedByTheChain() throws Exception {
        Cookie owner = elevatedOwner();
        String reference = referenceOfSessionBehind(owner);

        MockHttpServletResponse response = mockMvc.perform(
                        delete("/api/auth/sessions/{reference}", reference).with(csrf()))
                .andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void aFailureRecordingTheTerminationLeavesTheSessionAlive() throws Exception {
        Cookie owner = elevatedOwner();
        Cookie theirs = anEmployeeSignedIn();
        doThrow(new IllegalStateException("the event store is unavailable"))
                .when(appendAuthEventPort)
                .append(argThat(event -> event.action() == AuthAction.SESSION_TERMINATED));

        terminate(owner, referenceOfSessionBehind(theirs));

        assertThat(statusOfSessionRead(theirs))
                .as("the session must survive a termination that was never recorded")
                .isEqualTo(200);
    }

    @Test
    void aRefusalIsRecordedEvenWhenTheReferenceIsPercentEncoded() throws Exception {
        Cookie owner = elevatedOwner();
        Cookie employee = elevated(anEmployeeSignedIn());
        String reference = referenceOfSessionBehind(owner);

        MockHttpServletResponse response = terminateRaw(employee, percentEncodeFirstCharacterOf(reference));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(lastEventOf(AuthAction.SESSION_TERMINATION_DENIED).get("actor_user_id"))
                .as("an encoded path must leave the same record as a plain one")
                .isEqualTo(employeeId());
    }

    @Test
    void aRefusedListIsRecordedEvenWhenTheIdentifierIsPercentEncoded() throws Exception {
        elevatedOwner();
        Cookie employee = elevated(anEmployeeSignedIn());
        UUID owner = ownerId(OWNER_EMAIL);

        MockHttpServletResponse response = mockMvc.perform(get(URI.create(
                                "/api/auth/users/" + percentEncodeFirstCharacterOf(owner.toString()) + "/sessions"))
                        .cookie(employee))
                .andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(lastEventOf(AuthAction.SESSION_VIEW_DENIED).get("target_user_id"))
                .isEqualTo(owner);
    }

    @Test
    void terminatingASessionThatExpiredOnItsOwnRecordsNoTermination() throws Exception {
        Cookie owner = elevatedOwner();
        Cookie theirs = anEmployeeSignedIn();
        String reference = referenceOfSessionBehind(theirs);
        jdbc.update("delete from spring_session where session_id = ?", sessionIdOf(theirs));

        MockHttpServletResponse response = terminate(owner, reference);

        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(recordedActions())
                .as("nothing was ended, so nothing may be recorded as ended")
                .doesNotContain(AuthAction.SESSION_TERMINATED.name());
    }

    @Test
    void aSuccessfulCrossPersonListingIsRecordedWithActorAndTarget() throws Exception {
        Cookie owner = elevatedOwner();
        anEmployeeSignedIn();

        mockMvc.perform(get("/api/auth/users/{userId}/sessions", employeeId()).cookie(owner));

        Map<String, Object> recorded = lastEventOf(AuthAction.SESSIONS_OF_PERSON_LISTED);
        assertThat(recorded.get("actor_user_id")).isEqualTo(ownerId(OWNER_EMAIL));
        assertThat(recorded.get("target_user_id")).isEqualTo(employeeId());
    }

    @Test
    void theOwnerListingTheirOwnSessionsIsNotRecordedAsACrossPersonRead() throws Exception {
        Cookie owner = elevatedOwner();

        mockMvc.perform(get("/api/auth/sessions").cookie(owner));

        assertThat(recordedActions()).doesNotContain(AuthAction.SESSIONS_OF_PERSON_LISTED.name());
    }

    @Test
    void aTerminationThatFindsNothingIsRecordedAsAnAttemptRatherThanATermination() throws Exception {
        Cookie owner = elevatedOwner();

        terminate(owner, UUID.randomUUID().toString());

        assertThat(recordedActions())
                .contains(AuthAction.SESSION_TERMINATION_ATTEMPTED.name())
                .doesNotContain(AuthAction.SESSION_TERMINATED.name());
    }

    @Test
    void anAttemptOnAnExpiredSessionStillNamesThePersonItBelongedTo() throws Exception {
        Cookie owner = elevatedOwner();
        Cookie theirs = anEmployeeSignedIn();
        String reference = referenceOfSessionBehind(theirs);
        jdbc.update("delete from spring_session where session_id = ?", sessionIdOf(theirs));

        terminate(owner, reference);

        Map<String, Object> recorded = lastEventOf(AuthAction.SESSION_TERMINATION_ATTEMPTED);
        assertThat(recorded.get("actor_user_id")).isEqualTo(ownerId(OWNER_EMAIL));
        assertThat(recorded.get("target_user_id")).isEqualTo(employeeId());
    }

    private String percentEncodeFirstCharacterOf(String value) {
        return "%%%02X".formatted((int) value.charAt(0)) + value.substring(1);
    }

    private MockHttpServletResponse terminateRaw(Cookie session, String rawReference) throws Exception {
        return mockMvc.perform(delete(URI.create("/api/auth/sessions/" + rawReference))
                        .with(csrf())
                        .header("User-Agent", USER_AGENT)
                        .cookie(session))
                .andReturn()
                .getResponse();
    }

    private Cookie elevatedOwner() throws Exception {
        return elevated(sessionCookieOf(signUp(OWNER_EMAIL, VALID_PASSWORD)));
    }

    private Cookie elevated(Cookie session) throws Exception {
        mockMvc.perform(requestFor("/api/auth/reauthenticate", Map.of("password", VALID_PASSWORD))
                .cookie(session));
        return session;
    }

    private MockHttpServletResponse terminate(Cookie session, String reference) throws Exception {
        return mockMvc.perform(delete("/api/auth/sessions/{reference}", reference)
                        .with(csrf())
                        .header("User-Agent", USER_AGENT)
                        .cookie(session))
                .andReturn()
                .getResponse();
    }

    private int statusOfSessionRead(Cookie session) throws Exception {
        return mockMvc.perform(get("/api/auth/session").cookie(session))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private Map<String, Object> lastEventOf(AuthAction action) {
        return jdbc.queryForMap(
                "select * from auth_event where action = ? order by occurred_at desc limit 1", action.name());
    }
}
