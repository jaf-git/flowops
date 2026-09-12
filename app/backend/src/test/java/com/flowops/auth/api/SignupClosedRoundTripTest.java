package com.flowops.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.auth.AuthIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;

@Tag("AUTH-REGISTER-OWNER-01")
class SignupClosedRoundTripTest extends AuthIntegrationTest {
    private static final String THE_OWNER = "maria.enache@atelier.ro";
    private static final String A_STRANGER = "nobody-here@atelier.ro";

    @Test
    void askingForAPasscodeOnAClaimedInstallationSaysTheInstallationIsClaimed() throws Exception {
        signUp(THE_OWNER, VALID_PASSWORD);

        MockHttpServletResponse refused = askForAPasscode(A_STRANGER);

        assertThat(refused.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        JsonNode body = json.readTree(refused.getContentAsString());
        assertThat(body.get("code").asText()).isEqualTo("SIGNUP_CLOSED");
        assertThat(body.get("message").asText())
                .as("the person must be able to tell a closed door from a broken mail server")
                .contains("already has an owner");
    }

    @Test
    void completingSignupOnAClaimedInstallationRefusesForTheSameStatedReason() throws Exception {
        signUp(THE_OWNER, VALID_PASSWORD);

        MockHttpServletResponse refused = mockMvc.perform(requestFor(
                        "/api/auth/signup",
                        Map.of("email", A_STRANGER, "passcode", "123456", "password", VALID_PASSWORD)))
                .andReturn()
                .getResponse();

        assertThat(refused.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(json.readTree(refused.getContentAsString()).get("code").asText())
                .as("blaming the passcode sends them hunting for a code that was never the problem")
                .isEqualTo("SIGNUP_CLOSED");
    }

    @Test
    void theRefusalIsIdenticalForARegisteredAddressAndOneNobodyHasEverSeen() throws Exception {
        signUp(THE_OWNER, VALID_PASSWORD);

        MockHttpServletResponse forTheOwner = askForAPasscode(THE_OWNER);
        MockHttpServletResponse forAStranger = askForAPasscode(A_STRANGER);

        assertThat(forAStranger.getStatus()).isEqualTo(forTheOwner.getStatus());
        assertThat(forAStranger.getContentAsString())
                .as("a response that differs by address is an oracle for which addresses exist")
                .isEqualTo(forTheOwner.getContentAsString());
    }

    @Test
    void aClaimedInstallationStillIssuesNoPasscode() throws Exception {
        signUp(THE_OWNER, VALID_PASSWORD);
        long passcodesAfterTheOwner = passcodes.count();

        askForAPasscode(A_STRANGER);

        assertThat(passcodes.count())
                .as("a refused request must not leave a passcode behind for the address it refused")
                .isEqualTo(passcodesAfterTheOwner);
    }

    private MockHttpServletResponse askForAPasscode(String email) throws Exception {
        return mockMvc.perform(requestFor("/api/auth/signup/passcode", Map.of("email", email)))
                .andReturn()
                .getResponse();
    }
}
