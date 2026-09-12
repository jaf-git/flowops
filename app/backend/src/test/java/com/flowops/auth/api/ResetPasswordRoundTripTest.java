package com.flowops.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.flowops.auth.domain.model.EmailAddress;
import com.flowops.support.RoundTripClient;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("AUTH-RESET-PASSWORD-01")
class ResetPasswordRoundTripTest extends AuthRoundTripTest {
    private static final Pattern TOKEN_IN_LINK = Pattern.compile("token=(\\S+)");

    @Test
    void aSignedOutPersonSetsANewPasswordAndSignsInWithIt() {
        registerTheOwner();
        becomeAnAnonymousVisitor();

        String token = requestAResetAndReadTheLink();

        assertThat(browser.get("/api/auth/password-reset/" + token).getStatusCode())
                .as("an anonymous caller can open their own link")
                .isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> completed = complete(token, NEW_PASSWORD);
        assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        RoundTripClient afterwards = anotherBrowser();
        assertThat(signIn(afterwards, OWNER_EMAIL, NEW_PASSWORD).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(recordedActions()).contains("PASSWORD_RESET_REQUESTED", "PASSWORD_RESET_COMPLETED");
    }

    @Test
    void aSessionHeldBeforeTheResetStopsWorkingAfterIt() {
        registerTheOwner();
        RoundTripClient somebodyElse = anotherBrowser();
        assertThat(signIn(somebodyElse, OWNER_EMAIL, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(somebodyElse.holdsSession()).isTrue();

        becomeAnAnonymousVisitor();
        complete(requestAResetAndReadTheLink(), NEW_PASSWORD);

        assertThat(somebodyElse.get("/api/auth/sessions").getStatusCode())
                .as("the session somebody else held before the reset is gone")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void everyAddressGetsTheSameAnswerWhateverItTurnsOutToBe() {
        registerTheOwner();
        anEmployeeExists();
        jdbc.update("update auth_user set account_state = 'DEACTIVATED' where email = ?", EMPLOYEE_EMAIL);
        becomeAnAnonymousVisitor();

        Set<String> answers = new LinkedHashSet<>();
        for (String address : List.of(OWNER_EMAIL, "nimeni@atelier.ro", EMPLOYEE_EMAIL, "altcineva@example.test")) {
            ResponseEntity<String> answered =
                    browser.post("/api/auth/password-reset/request", "{\"email\":\"%s\"}".formatted(address));
            answers.add(answered.getStatusCode() + " " + answered.getBody());
        }

        assertThat(answers)
                .as("an account, no account, a deactivated account and a stranger are one answer, not four")
                .hasSize(1);
        assertThat(answers.iterator().next()).contains(HttpStatus.ACCEPTED.toString());
    }

    @Test
    void theThreeWaysALinkCanBeDeadAnswerIdentically() {
        registerTheOwner();
        becomeAnAnonymousVisitor();

        String expired = requestAResetAndReadTheLink();
        jdbc.update("update auth_password_reset_token set expires_at = now() - interval '1 hour'");
        String spent = requestAResetAndReadTheLink();
        jdbc.update("update auth_password_reset_token set spent_at = now() where expires_at > now()");

        assertThat(jdbc.queryForObject("select count(*) from auth_password_reset_token", Integer.class))
                .as("two real rows in two different states, not one row and a guess")
                .isEqualTo(2);

        Set<String> answers = new LinkedHashSet<>();
        for (String token : List.of("a-token-that-was-never-minted", expired, spent)) {
            ResponseEntity<String> refused = browser.get("/api/auth/password-reset/" + token);
            answers.add(refused.getStatusCode() + " " + refused.getBody());
        }

        assertThat(answers).hasSize(1);
        assertThat(answers.iterator().next())
                .contains(HttpStatus.GONE.toString())
                .contains("RESET_TOKEN_UNUSABLE");
    }

    @Test
    void completingAResetSpendsTheLinkSoTheVeryNextCheckOfItIsRefused() {
        registerTheOwner();
        becomeAnAnonymousVisitor();
        String token = requestAResetAndReadTheLink();

        assertThat(browser.get("/api/auth/password-reset/" + token).getStatusCode())
                .as("good right up to the moment it is used")
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(complete(token, NEW_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> afterwards = browser.get("/api/auth/password-reset/" + token);

        assertThat(afterwards.getStatusCode())
                .as("the same link, one instant later, having been spent by its own success")
                .isEqualTo(HttpStatus.GONE);
        assertThat(afterwards.getBody()).contains("RESET_TOKEN_UNUSABLE");
        assertThat(jdbc.queryForObject(
                        "select count(*) from auth_password_reset_token where spent_at is not null", Integer.class))
                .as("spent by the product, not by a fixture")
                .isEqualTo(1);
    }

    @Test
    void aRefusedPasswordDoesNotSpendTheLinkAndTheSameLinkStillWorks() {
        registerTheOwner();
        becomeAnAnonymousVisitor();
        String token = requestAResetAndReadTheLink();

        ResponseEntity<String> refused = complete(token, "short");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody()).contains("PASSWORD_POLICY_VIOLATION").contains("MINIMUM_LENGTH");
        assertThat(jdbc.queryForObject(
                        "select count(*) from auth_password_reset_token where spent_at is null", Integer.class))
                .as("a typo must not cost them the link")
                .isEqualTo(1);

        assertThat(complete(token, NEW_PASSWORD).getStatusCode())
                .as("the same link, used again after the typo")
                .isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void requestingAResetAndNeverUsingItLeavesTheOldPasswordWorking() {
        registerTheOwner();
        becomeAnAnonymousVisitor();
        requestAResetAndReadTheLink();

        RoundTripClient afterwards = anotherBrowser();
        assertThat(signIn(afterwards, OWNER_EMAIL, PASSWORD).getStatusCode())
                .as("the most likely person to request a reset and then remember is the account's owner")
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void noAnswerOnThisPathEverEchoesTheToken() {
        registerTheOwner();
        becomeAnAnonymousVisitor();
        String token = requestAResetAndReadTheLink();

        assertThat(bodyOrEmpty(
                        browser.post("/api/auth/password-reset/request", "{\"email\":\"%s\"}".formatted(OWNER_EMAIL))))
                .doesNotContain(token);
        assertThat(bodyOrEmpty(browser.get("/api/auth/password-reset/" + token)))
                .doesNotContain(token);
        assertThat(jdbc.queryForList("select token_hash from auth_password_reset_token", String.class))
                .as("the clear token is in the message and in no column")
                .noneMatch(stored -> stored.contains(token));
    }

    private String requestAResetAndReadTheLink() {
        int before = mockingDetails(mailDispatcher).getInvocations().size();
        ResponseEntity<String> asked =
                browser.post("/api/auth/password-reset/request", "{\"email\":\"%s\"}".formatted(OWNER_EMAIL));
        assertThat(asked.getStatusCode())
                .as("the request endpoint answered %s", asked.getBody())
                .isEqualTo(HttpStatus.ACCEPTED);

        ArgumentCaptor<String> delivered = ArgumentCaptor.forClass(String.class);
        verify(mailDispatcher, timeout(10_000).times(before + 1))
                .send(any(EmailAddress.class), anyString(), delivered.capture());
        Matcher token = TOKEN_IN_LINK.matcher(delivered.getAllValues().getLast());
        if (!token.find()) {
            throw new AssertionError("the message that followed the request carried no reset link");
        }
        return token.group(1);
    }

    private String bodyOrEmpty(ResponseEntity<String> response) {
        return response.getBody() == null ? "" : response.getBody();
    }

    private ResponseEntity<String> complete(String token, String password) {
        return browser.post(
                "/api/auth/password-reset/complete",
                "{\"token\":\"%s\",\"newPassword\":\"%s\"}".formatted(token, password));
    }

    private void becomeAnAnonymousVisitor() {
        browser.forget();
        browser.get("/api/auth/session");
    }
}
