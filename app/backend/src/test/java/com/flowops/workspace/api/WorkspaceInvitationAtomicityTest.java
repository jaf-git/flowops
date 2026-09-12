package com.flowops.workspace.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.flowops.auth.domain.model.EmailAddress;
import com.flowops.support.RoundTripTest;
import com.flowops.workspace.application.shared.port.AppendWorkspaceEventPort;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@Tag("WORKSPACE-INVITE-01")
class WorkspaceInvitationAtomicityTest extends RoundTripTest {
    private static final String OWNER_EMAIL = "maria@atelier.ro";
    private static final String COLLEAGUE_EMAIL = "ionut@atelier.ro";
    private static final String PASSWORD = "a-long-enough-passphrase";
    private static final Pattern PASSCODE_IN_BODY = Pattern.compile("code is (\\S+)\\.");

    @MockitoSpyBean
    private AppendWorkspaceEventPort workspacePersistence;

    @Test
    void whenTheEventAppendFailsNoInvitationSurvivesAndNoMessageIsSent() throws Exception {
        String managerId = registerAndSetUp();

        doThrow(new IllegalStateException("the event store is unreachable"))
                .when(workspacePersistence)
                .append(any());

        ResponseEntity<String> attempted = browser.post(
                "/api/workspace/invitations",
                "{\"emailAddress\":\"%s\",\"role\":\"EMPLOYEE\",\"managerId\":\"%s\"}"
                        .formatted(COLLEAGUE_EMAIL, managerId));

        assertThat(attempted.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        assertThat(jdbc.queryForObject("select count(*) from workspace_invitation", Long.class))
                .as("the invitation must not survive a transaction that unwound")
                .isZero();

        verifyNoInteractions(mailSender);
    }

    private String registerAndSetUp() throws Exception {
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

        ResponseEntity<String> setUp = browser.post(
                "/api/workspace/setup",
                """
                {"ownerName":"Maria Ionescu","workspaceName":"Atelier Ionescu",
                 "use":"WORK","timezone":"Europe/Bucharest"}
                """);
        assertThat(setUp.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Map<String, Object>> roots =
                jdbc.queryForList("select id from workspace_membership where manager_id is null");
        assertThat(roots).hasSize(1);
        return roots.getFirst().get("id").toString();
    }
}
