package com.flowops.workspace.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.flowops.auth.domain.model.EmailAddress;
import com.flowops.support.RoundTripClient;
import com.flowops.support.RoundTripTest;
import com.flowops.workspace.application.shared.port.AppendWorkspaceEventPort;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@Tag("WORKSPACE-REVOKE-INVITE-01")
class WorkspaceRevokeConcurrencyTest extends RoundTripTest {
    private static final String OWNER_EMAIL = "maria@atelier.ro";
    private static final String INVITED_EMAIL = "ionut@atelier.ro";
    private static final String PASSWORD = "a-long-enough-passphrase";
    private static final Pattern PASSCODE_IN_BODY = Pattern.compile("code is (\\S+)\\.");

    @MockitoSpyBean
    private AppendWorkspaceEventPort workspacePersistence;

    @Test
    void whenTheEventCannotBeAppendedTheInvitationIsNotRevokedEither() throws Exception {
        String invitation = registerSetUpAndInvite();
        doThrow(new IllegalStateException("the event log is unavailable"))
                .when(workspacePersistence)
                .append(any());

        ResponseEntity<String> response =
                browser.post("/api/workspace/invitations/%s/revoke".formatted(invitation), "");

        assertThat(response.getStatusCode().is5xxServerError()).isTrue();
        assertThat(jdbc.queryForObject(
                        "select state from workspace_invitation where id = ?::uuid", String.class, invitation))
                .as("the revocation must not survive an event that could not be appended")
                .isEqualTo("SENT");
    }

    @Test
    void twoRevocationsOfOneInvitationProduceExactlyOneWithdrawal() throws Exception {
        String invitation = registerSetUpAndInvite();
        String path = "/api/workspace/invitations/%s/revoke".formatted(invitation);

        RoundTripClient first = signedInBrowser();
        RoundTripClient second = signedInBrowser();

        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            Callable<ResponseEntity<String>> revokeFirst = () -> first.post(path, "");
            Callable<ResponseEntity<String>> revokeSecond = () -> second.post(path, "");

            List<Future<ResponseEntity<String>>> answers = threads.invokeAll(List.of(revokeFirst, revokeSecond));

            for (Future<ResponseEntity<String>> answer : answers) {
                assertThat(answer.get().getStatusCode())
                        .as("neither caller is told something went wrong; the second is simply inert")
                        .isEqualTo(HttpStatus.OK);
            }
        } finally {
            threads.shutdownNow();
        }

        assertThat(jdbc.queryForObject(
                        "select count(*) from workspace_event where action = 'INVITATION_REVOKED'", Long.class))
                .as("one withdrawal, one event — two would claim it was revoked twice")
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                        "select state from workspace_invitation where id = ?::uuid", String.class, invitation))
                .isEqualTo("REVOKED");
    }

    private RoundTripClient signedInBrowser() {
        RoundTripClient client = new RoundTripClient(rest);
        client.get("/api/auth/session");
        assertThat(client.post(
                                "/api/auth/login",
                                "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(OWNER_EMAIL, PASSWORD))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        return client;
    }

    private String registerSetUpAndInvite() throws Exception {
        browser.get("/api/auth/session");
        browser.post("/api/auth/signup/passcode", "{\"email\":\"" + OWNER_EMAIL + "\"}");

        ArgumentCaptor<String> delivered = ArgumentCaptor.forClass(String.class);
        verify(mailDispatcher, atLeastOnce()).send(any(EmailAddress.class), anyString(), delivered.capture());
        Matcher code = PASSCODE_IN_BODY.matcher(delivered.getAllValues().getLast());
        if (!code.find()) {
            throw new AssertionError("no passcode was delivered to " + OWNER_EMAIL);
        }

        assertThat(browser.post(
                                "/api/auth/signup",
                                "{\"email\":\"%s\",\"passcode\":\"%s\",\"password\":\"%s\"}"
                                        .formatted(OWNER_EMAIL, code.group(1), PASSWORD))
                        .getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(browser.post(
                                "/api/workspace/setup",
                                """
                                {"ownerName":"Maria Ionescu","workspaceName":"Atelier Ionescu",
                                 "use":"WORK","timezone":"Europe/Bucharest"}
                                """)
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        List<Map<String, Object>> roots =
                jdbc.queryForList("select id from workspace_membership where manager_id is null");
        ResponseEntity<String> invited = browser.post(
                "/api/workspace/invitations",
                "{\"emailAddress\":\"%s\",\"role\":\"EMPLOYEE\",\"managerId\":\"%s\"}"
                        .formatted(INVITED_EMAIL, roots.getFirst().get("id")));
        assertThat(invited.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(invited.getBody()).get("id").asText();
    }
}
