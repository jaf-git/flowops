package com.flowops.workspace.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.flowops.auth.domain.model.EmailAddress;
import com.flowops.support.RoundTripTest;
import com.flowops.workspace.application.shared.port.AppendWorkspaceEventPort;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@Tag("WORKSPACE-ERASE-PERSON-01")
class WorkspaceErasureAtomicityTest extends RoundTripTest {
    private static final String OWNER_EMAIL = "maria@atelier.ro";
    private static final String LEAVER_EMAIL = "ionut@atelier.ro";
    private static final String LEAVER_NAME = "Ionuț Petrescu";
    private static final String PASSWORD = "a-long-enough-passphrase";
    private static final Pattern PASSCODE_IN_BODY = Pattern.compile("code is (\\S+)\\.");

    @MockitoSpyBean
    private AppendWorkspaceEventPort appendWorkspaceEventPort;

    @Test
    void whenTheLastWriteFailsNothingAboutThePersonHasBeenDestroyed() throws Exception {
        registerTheOwnerAndSetUp();
        String leaver = seedLeaver();
        String leaverAccount = accountOf(leaver);
        assertThat(browser.post("/api/workspace/people/" + leaver + "/deactivate", "")
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(browser.post("/api/auth/reauthenticate", "{\"password\":\"%s\"}".formatted(PASSWORD))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        doThrow(new IllegalStateException("the event could not be appended"))
                .when(appendWorkspaceEventPort)
                .append(any());

        assertThat(browser.post(
                                "/api/workspace/people/" + leaver + "/erase",
                                "{\"typedName\":\"%s\"}".formatted(LEAVER_NAME))
                        .getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        assertThat(jdbc.queryForObject("select email from auth_user where id = ?::uuid", String.class, leaverAccount))
                .as("the address is exactly where it was; AUTH's writes are not in a transaction of their own")
                .isEqualTo(LEAVER_EMAIL);
        assertThat(jdbc.queryForObject(
                        "select display_name from auth_user where id = ?::uuid", String.class, leaverAccount))
                .isEqualTo(LEAVER_NAME);
        assertThat(jdbc.queryForObject(
                        "select count(*) from auth_credential where user_id = ?::uuid", Integer.class, leaverAccount))
                .as("the credential is still there, so a failed erasure has not locked them out either")
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select status from workspace_membership where id = ?::uuid", String.class, leaver))
                .as(
                        "still merely deactivated: the erasure can be retried, which is the whole point of it failing whole")
                .isEqualTo("DEACTIVATED");
    }

    private String accountOf(String membershipId) {
        return jdbc.queryForObject(
                "select user_id::text from workspace_membership where id = ?::uuid", String.class, membershipId);
    }

    private String seedLeaver() {
        UUID userId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        UUID workspaceId = jdbc.queryForObject("select id from workspace", UUID.class);
        String root =
                jdbc.queryForObject("select id::text from workspace_membership where manager_id is null", String.class);

        jdbc.update(
                "insert into auth_user (id, email, account_state, role_name, created_at, setup_completed, display_name)"
                        + " values (?, ?, 'ACTIVE', 'MANAGER', ?, true, ?)",
                userId,
                LEAVER_EMAIL,
                OffsetDateTime.now(),
                LEAVER_NAME);
        jdbc.update(
                "insert into auth_credential (user_id, password_hash, algorithm, updated_at) values (?, ?, 'bcrypt', ?)",
                userId,
                seededHashOf(PASSWORD),
                OffsetDateTime.now());
        jdbc.update(
                "insert into workspace_membership (id, workspace_id, user_id, status, manager_id, joined_at)"
                        + " values (?, ?, ?, 'ACTIVE', ?::uuid, ?)",
                membershipId,
                workspaceId,
                userId,
                root,
                OffsetDateTime.now());
        return membershipId.toString();
    }

    private void registerTheOwnerAndSetUp() throws Exception {
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
    }
}
