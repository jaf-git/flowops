package com.flowops.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.auth.domain.model.EmailAddress;
import com.flowops.support.RoundTripClient;
import com.flowops.support.RoundTripTest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public abstract class AuthRoundTripTest extends RoundTripTest {
    protected static final String OWNER_EMAIL = "maria@atelier.ro";
    protected static final String EMPLOYEE_EMAIL = "ionut@atelier.ro";
    protected static final String PASSWORD = "a-long-enough-passphrase";
    protected static final String NEW_PASSWORD = "corect-cal-baterie-capsator";

    private static final Pattern PASSCODE_IN_BODY = Pattern.compile("code is (\\S+)\\.");

    protected void registerTheOwner() {
        browser.get("/api/auth/session");
        browser.post("/api/auth/signup/passcode", "{\"email\":\"" + OWNER_EMAIL + "\"}");

        ResponseEntity<String> signedUp = browser.post(
                "/api/auth/signup",
                "{\"email\":\"%s\",\"passcode\":\"%s\",\"password\":\"%s\"}"
                        .formatted(OWNER_EMAIL, lastDeliveredPasscode(), PASSWORD));

        assertThat(signedUp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(browser.holdsSession()).isTrue();
    }

    protected String lastDeliveredPasscode() {
        ArgumentCaptor<String> delivered = ArgumentCaptor.forClass(String.class);
        verify(mailDispatcher, atLeastOnce()).send(any(EmailAddress.class), anyString(), delivered.capture());
        Matcher code = PASSCODE_IN_BODY.matcher(delivered.getAllValues().getLast());
        if (!code.find()) {
            throw new AssertionError("no passcode was delivered");
        }
        return code.group(1);
    }

    protected UUID anEmployeeExists() {
        UUID employeeId = UUID.randomUUID();
        jdbc.update(
                """
                insert into auth_user (id, email, account_state, role_name, created_at)
                values (?, ?, 'ACTIVE', 'EMPLOYEE', now())
                """,
                employeeId,
                EMPLOYEE_EMAIL);
        jdbc.update(
                """
                insert into auth_credential (user_id, password_hash, algorithm, updated_at)
                select ?, password_hash, algorithm, updated_at from auth_credential where user_id = ?
                """,
                employeeId,
                identityOf(OWNER_EMAIL));
        return employeeId;
    }

    protected ResponseEntity<String> signIn(String email, String password) {
        return signIn(browser, email, password);
    }

    protected ResponseEntity<String> signIn(RoundTripClient client, String email, String password) {
        return client.post("/api/auth/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password));
    }

    protected RoundTripClient anotherBrowser() {
        RoundTripClient client = new RoundTripClient(rest);
        client.get("/api/auth/session");
        return client;
    }

    protected ResponseEntity<String> reauthenticate(RoundTripClient client, String password) {
        return client.post("/api/auth/reauthenticate", "{\"password\":\"%s\"}".formatted(password));
    }

    protected JsonNode bodyOf(ResponseEntity<String> response) throws Exception {
        return json.readTree(response.getBody());
    }

    protected UUID identityOf(String email) {
        return jdbc.queryForObject("select id from auth_user where email = ?", UUID.class, email);
    }

    protected List<String> recordedActions() {
        return jdbc.queryForList("select action from auth_event", String.class);
    }

    protected List<Map<String, Object>> recordedRowsFor(String action) {
        return jdbc.queryForList("select actor_user_id, target_user_id from auth_event where action = ?", action);
    }

    protected String sessionIdentifierHeldBy(RoundTripClient client) {
        return new String(Base64.getDecoder().decode(client.cookie("SESSION")), StandardCharsets.UTF_8);
    }
}
