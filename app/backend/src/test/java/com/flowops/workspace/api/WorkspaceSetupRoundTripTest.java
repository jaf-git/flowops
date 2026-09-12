package com.flowops.workspace.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.auth.domain.model.EmailAddress;
import com.flowops.support.RoundTripTest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("WORKSPACE-SETUP-01")
class WorkspaceSetupRoundTripTest extends RoundTripTest {
    private static final String OWNER_EMAIL = "maria@atelier.ro";
    private static final String PASSWORD = "a-long-enough-passphrase";
    private static final Pattern PASSCODE_IN_BODY = Pattern.compile("code is (\\S+)\\.");

    @Test
    void anAnonymousCallerIsRefusedTheSetupPrefill() {
        ResponseEntity<String> response = browser.get("/api/workspace/setup");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void anAnonymousCallerIsRefusedTheSetupAction() {
        ResponseEntity<String> response = browser.post(
                "/api/workspace/setup",
                """
                {"ownerName":"Maria Ionescu","workspaceName":"Atelier Ionescu","use":"WORK","timezone":"Europe/Bucharest"}
                """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(jdbc.queryForObject("select name from workspace", String.class))
                .isNull();
    }

    @Test
    void anOwnerRegistersAndSetsTheWorkspaceUpOverHttp() throws Exception {
        registerTheOwner();

        ResponseEntity<String> prefill = browser.get("/api/workspace/setup");
        assertThat(prefill.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode offered = json.readTree(prefill.getBody());
        assertThat(offered.get("setupCompleted").asBoolean()).isFalse();
        assertThat(offered.get("suggestedTimezone").asText()).isNotBlank();
        assertThat(offered.get("availableTimezones")).isNotEmpty();

        ResponseEntity<String> setUp = browser.post(
                "/api/workspace/setup",
                """
                {"ownerName":"Maria Ionescu","workspaceName":"Atelier Ionescu","use":"WORK","timezone":"Europe/Bucharest"}
                """);

        assertThat(setUp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = json.readTree(setUp.getBody());
        assertThat(body.get("workspace").get("name").asText()).isEqualTo("Atelier Ionescu");
        assertThat(body.get("workspace").get("use").asText()).isEqualTo("WORK");
        assertThat(body.get("workspace").get("timezone").asText()).isEqualTo("Europe/Bucharest");
        assertThat(body.get("landingTarget").asText()).isNotBlank();

        assertThat(jdbc.queryForObject("select name from workspace", String.class))
                .isEqualTo("Atelier Ionescu");
        assertThat(jdbc.queryForObject("select display_name from auth_user where email = ?", String.class, OWNER_EMAIL))
                .isEqualTo("Maria Ionescu");
        assertThat(jdbc.queryForObject(
                        "select timezone from workspace_settings where effective_to is null", String.class))
                .isEqualTo("Europe/Bucharest");
        assertThat(jdbc.queryForObject("select count(*) from workspace_event", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void aRefusalNamesTheFieldOverTheWire() throws Exception {
        registerTheOwner();

        ResponseEntity<String> response = browser.post(
                "/api/workspace/setup",
                """
                {"ownerName":"Maria Ionescu","workspaceName":"  ","use":"WORK","timezone":"Europe/Bucharest"}
                """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode envelope = json.readTree(response.getBody());
        assertThat(envelope.get("code").asText()).isNotBlank();
        assertThat(envelope.get("details").toString()).contains("workspaceName");
    }

    @Test
    void aRequestWithoutTheCrossSiteTokenIsRefused() throws Exception {
        registerTheOwner();

        ResponseEntity<String> response = browser.withoutCrossSiteToken(
                HttpMethod.POST,
                "/api/workspace/setup",
                """
                {"ownerName":"Maria Ionescu","workspaceName":"Atelier Ionescu","use":"WORK","timezone":"Europe/Bucharest"}
                """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(jdbc.queryForObject("select name from workspace", String.class))
                .isNull();
    }

    private void registerTheOwner() throws Exception {
        browser.get("/api/auth/session");
        browser.post("/api/auth/signup/passcode", "{\"email\":\"" + OWNER_EMAIL + "\"}");

        ArgumentCaptor<String> delivered = ArgumentCaptor.forClass(String.class);
        verify(mailDispatcher, atLeastOnce()).send(any(EmailAddress.class), anyString(), delivered.capture());
        Matcher code = PASSCODE_IN_BODY.matcher(delivered.getAllValues().getLast());
        if (!code.find()) {
            throw new AssertionError("no passcode was delivered to " + OWNER_EMAIL);
        }

        ResponseEntity<String> signedUp = browser.post(
                "/api/auth/signup",
                "{\"email\":\"%s\",\"passcode\":\"%s\",\"password\":\"%s\"}"
                        .formatted(OWNER_EMAIL, code.group(1), PASSWORD));
        assertThat(signedUp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
