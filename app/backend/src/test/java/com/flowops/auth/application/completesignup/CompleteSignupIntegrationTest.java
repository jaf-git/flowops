package com.flowops.auth.application.completesignup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowops.auth.AuthIntegrationTest;
import com.flowops.auth.domain.enums.AuthAction;
import com.flowops.auth.domain.model.EmailAddress;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

@Tag("AUTH-REGISTER-OWNER-01")
class CompleteSignupIntegrationTest extends AuthIntegrationTest {
    private static final String EMAIL = "founder@flowops.test";

    @Test
    void theOwnerTheCredentialAndTheCompletionEventCommitTogether() throws Exception {
        MockHttpServletResponse response = signUp(EMAIL, VALID_PASSWORD);

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(users.findByEmail(EMAIL)).isPresent();
        assertThat(credentials.count()).isEqualTo(1);
        assertThat(recordedActions()).contains(AuthAction.SIGNUP_COMPLETED.name());
        assertThat(passcodes.findFirstByEmailOrderByIssuedAtDesc(EMAIL))
                .hasValueSatisfying(passcode -> assertThat(passcode.isUsed()).isTrue());
    }

    @Test
    void requestingAPasscodeCreatesNoAccount() throws Exception {
        passcodeIssuedTo(EMAIL);

        assertThat(users.count()).isZero();
        assertThat(credentials.count()).isZero();
        assertThat(passcodes.findFirstByEmailOrderByIssuedAtDesc(EMAIL)).isPresent();
    }

    @Test
    void theNewOwnerHoldsEveryPermissionTheSeededOwnerRoleBundles() throws Exception {
        MockHttpServletResponse response = signUp(EMAIL, VALID_PASSWORD);

        assertThat(response.getContentAsString())
                .contains("SESSION_VIEW_OWN")
                .contains("SESSION_END_OWN")
                .contains("CREDENTIAL_REAUTH_OWN")
                .contains("CREDENTIAL_CHANGE_OWN")
                .contains("SESSION_VIEW_ANY")
                .contains("SESSION_TERMINATE_ANY");
    }

    @Test
    void aFailureInTheLastWriteRollsBackTheWholeSignup() throws Exception {
        doThrow(new IllegalStateException("event store unavailable"))
                .when(appendAuthEventPort)
                .append(argThat(event -> event.action() == AuthAction.SIGNUP_COMPLETED));

        MockHttpServletResponse response = signUp(EMAIL, VALID_PASSWORD);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(users.count()).isZero();
        assertThat(credentials.count()).isZero();
        assertThat(recordedActions()).doesNotContain(AuthAction.SIGNUP_COMPLETED.name());
    }

    @Test
    void aRejectedPasscodeStillCommitsItsFailureCountAndItsFailureEvent() throws Exception {
        passcodeIssuedTo(EMAIL);

        mockMvc.perform(requestFor(
                        "/api/auth/signup", Map.of("email", EMAIL, "passcode", "000000", "password", VALID_PASSWORD)))
                .andExpect(status().isUnauthorized());

        assertThat(passcodes.findFirstByEmailOrderByIssuedAtDesc(EMAIL))
                .hasValueSatisfying(
                        passcode -> assertThat(passcode.getFailureCount()).isEqualTo(1));
        assertThat(recordedActions()).contains(AuthAction.SIGNUP_FAILED.name());
        assertThat(users.count()).isZero();
    }

    @Test
    void aPasswordThatBreaksThePolicyNamesTheRuleAndWritesNoAccount() throws Exception {
        String passcode = passcodeIssuedTo(EMAIL);

        mockMvc.perform(requestFor(
                        "/api/auth/signup", Map.of("email", EMAIL, "passcode", passcode, "password", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains("PASSWORD_POLICY_VIOLATION")
                        .contains("MINIMUM_LENGTH"));

        assertThat(users.count()).isZero();
        assertThat(credentials.count()).isZero();
    }

    @Test
    void onceTheInstallationHasAnOwnerNoSecondPersonCanSignUp() throws Exception {
        signUp(EMAIL, VALID_PASSWORD);
        assertThat(users.count()).isEqualTo(1);

        MockHttpServletResponse response = mockMvc.perform(
                        requestFor("/api/auth/signup/passcode", Map.of("email", "stranger@flowops.test")))
                .andReturn()
                .getResponse();

        assertThat(response.getStatus())
                .as("a claimed installation says so, rather than answering as though it had sent something")
                .isEqualTo(409);
        assertThat(users.count()).isEqualTo(1);
        assertThat(passcodes.findFirstByEmailOrderByIssuedAtDesc("stranger@flowops.test"))
                .isEmpty();
        verify(mailDispatcher, never()).send(eq(new EmailAddress("stranger@flowops.test")), anyString(), anyString());
    }

    @Test
    void aClaimedInstallationAnswersEveryAddressIdentically() throws Exception {
        signUp(EMAIL, VALID_PASSWORD);

        MockHttpServletResponse registered = mockMvc.perform(
                        requestFor("/api/auth/signup/passcode", Map.of("email", EMAIL)))
                .andReturn()
                .getResponse();
        MockHttpServletResponse stranger = mockMvc.perform(
                        requestFor("/api/auth/signup/passcode", Map.of("email", "stranger@flowops.test")))
                .andReturn()
                .getResponse();

        assertThat(registered.getStatus()).isEqualTo(stranger.getStatus());
        assertThat(registered.getContentAsString()).isEqualTo(stranger.getContentAsString());
        assertThat(recordedActions()).doesNotContain(AuthAction.DUPLICATE_SIGNUP_ATTEMPT.name());
    }

    @Test
    void aSignupWithoutTheCrossSiteTokenIsRefused() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("email", EMAIL, "passcode", "000000", "password", VALID_PASSWORD))))
                .andExpect(status().isForbidden());

        assertThat(users.count()).isZero();
    }

    @Test
    void noEventRowCarriesThePasswordOrThePasscode() throws Exception {
        String passcode = passcodeIssuedTo(EMAIL);
        mockMvc.perform(requestFor(
                        "/api/auth/signup", Map.of("email", EMAIL, "passcode", passcode, "password", VALID_PASSWORD)))
                .andExpect(status().isCreated());

        assertThat(everyRecordedEventValue()).isNotEmpty().allSatisfy(row -> assertThat(row)
                .doesNotContain(VALID_PASSWORD)
                .doesNotContain(passcode));
    }
}
