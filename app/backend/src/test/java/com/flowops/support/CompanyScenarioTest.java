package com.flowops.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.flowops.auth.domain.model.EmailAddress;
import com.flowops.tasklib.application.published.TemplateResolutionUseCase;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

public abstract class CompanyScenarioTest extends RoundTripTest {
    protected static final String OWNER_EMAIL = "maria@atelier.ro";
    protected static final String PASSWORD = "a-long-enough-passphrase";
    private static final Pattern PASSCODE_IN_BODY = Pattern.compile("code is (\\S+)\\.");

    protected static final String AGENCY_OWNER = "Agency owner";

    protected static final String ACCOUNT_MANAGER = "Account manager";

    protected static final String EDITOR = "Editor";

    protected static final String CONTENT_WRITER = "Content writer";

    protected static final String DESIGNER = "Designer";

    protected record Company(UUID maria, UUID ionut, UUID ioana, UUID andrei, UUID elena) {}

    protected record Seeded(UUID user, String membership) {}

    @Autowired
    private TemplateResolutionUseCase taskTemplates;

    protected UUID work(String title) {
        UUID author = jdbc.queryForObject("select id from auth_user where email = ?", UUID.class, OWNER_EMAIL);
        return taskTemplates.resolve(title, null, author);
    }

    protected Company buildTheCompany() throws Exception {
        registerTheOwnerAndSetUp();
        String mariaMembership =
                jdbc.queryForObject("select id::text from workspace_membership where manager_id is null", String.class);
        UUID maria = jdbc.queryForObject(
                "select user_id from workspace_membership where id = ?::uuid", UUID.class, mariaMembership);

        Seeded ionut = seed("ionut@atelier.ro", "Ionuț Petrescu", "MANAGER", mariaMembership);
        Seeded ioana = seed("ioana@atelier.ro", "Ioana Radu", "MANAGER", ionut.membership);
        Seeded andrei = seed("andrei@atelier.ro", "Andrei Munteanu", "EMPLOYEE", ioana.membership);
        Seeded elena = seed("elena@atelier.ro", "Elena Dobre", "EMPLOYEE", mariaMembership);

        does(maria, AGENCY_OWNER);
        does(ionut.user, ACCOUNT_MANAGER);
        does(ioana.user, EDITOR);
        does(andrei.user, CONTENT_WRITER);
        does(elena.user, DESIGNER);

        return new Company(maria, ionut.user, ioana.user, andrei.user, elena.user);
    }

    protected UUID role(String name) {
        return jdbc.queryForObject("select id from functional_role where name = ?", UUID.class, name);
    }

    protected UUID does(UUID person, String roleName) {
        UUID roleId = role(roleName);
        int updated =
                jdbc.update("update workspace_membership set functional_role_id = ? where user_id = ?", roleId, person);
        assertThat(updated)
                .as("%s must actually have been given a stated job, or this file proves nothing", person)
                .isEqualTo(1);
        return roleId;
    }

    protected void hasNoStatedJob(UUID person) {
        int updated =
                jdbc.update("update workspace_membership set functional_role_id = null where user_id = ?", person);
        assertThat(updated)
                .as("%s must actually have been returned to having no stated job, or the control is not one", person)
                .isEqualTo(1);
    }

    protected UUID statedJobOf(UUID person) {
        return jdbc.queryForObject(
                "select functional_role_id from workspace_membership where user_id = ?", UUID.class, person);
    }

    protected Seeded seed(String email, String displayName, String role, String managerId) {
        UUID userId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        UUID workspaceId = jdbc.queryForObject("select id from workspace", UUID.class);

        jdbc.update(
                "insert into auth_user (id, email, account_state, role_name, created_at, setup_completed, display_name)"
                        + " values (?, ?, 'ACTIVE', ?, ?, true, ?)",
                userId,
                email,
                role,
                OffsetDateTime.now(),
                displayName);
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
                managerId,
                OffsetDateTime.now().minusDays(90));
        return new Seeded(userId, membershipId.toString());
    }

    protected RoundTripClient signedInBrowser(String email) {
        RoundTripClient client = new RoundTripClient(rest);
        signIn(client, email);
        return client;
    }

    protected void signIn(RoundTripClient client, String email) {
        client.get("/api/auth/session");
        assertThat(client.post("/api/auth/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    protected void registerTheOwnerAndSetUp() throws Exception {
        browser.get("/api/auth/session");
        browser.post("/api/auth/signup/passcode", "{\"email\":\"" + OWNER_EMAIL + "\"}");

        org.mockito.ArgumentCaptor<String> delivered = org.mockito.ArgumentCaptor.forClass(String.class);

        verify(mailDispatcher, timeout(10_000).atLeastOnce())
                .send(any(EmailAddress.class), anyString(), delivered.capture());
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
