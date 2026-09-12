package com.flowops.workspace.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.auth.domain.model.EmailAddress;
import com.flowops.support.RoundTripTest;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("WORKSPACE-EDIT-REPORTING-LINE-01")
class WorkspaceReassignRoundTripTest extends RoundTripTest {
    private static final String OWNER_EMAIL = "maria@atelier.ro";
    private static final String PASSWORD = "a-long-enough-passphrase";
    private static final Pattern PASSCODE_IN_BODY = Pattern.compile("code is (\\S+)\\.");

    @Test
    void anAnonymousCallerCannotMoveAnybody() {
        ResponseEntity<String> response = browser.post(
                "/api/workspace/people/%s/manager".formatted(UUID.randomUUID()),
                "{\"proposedManagerId\":\"%s\"}".formatted(UUID.randomUUID()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void theOwnerMovesSomebodyAndBothManagersAreRecorded() throws Exception {
        Company company = buildTheCompany();

        ResponseEntity<String> moved = browser.post(
                "/api/workspace/people/%s/manager".formatted(company.andrei),
                "{\"proposedManagerId\":\"%s\"}".formatted(company.ionut));

        assertThat(moved.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = json.readTree(moved.getBody());
        assertThat(body.get("changed").asBoolean()).isTrue();
        assertThat(body.get("formerManagerId").asText()).isEqualTo(company.ioana);
        assertThat(body.get("newManagerId").asText()).isEqualTo(company.ionut);

        assertThat(jdbc.queryForObject(
                        "select manager_id from workspace_membership where id = ?::uuid", String.class, company.andrei))
                .isEqualTo(company.ionut);

        Map<String, Object> event = jdbc.queryForMap(
                """
                select actor_user_id::text as actor,
                       subject_user_id::text as subject,
                       former_manager_user_id::text as former_manager,
                       new_manager_user_id::text as new_manager
                  from workspace_event
                 where action = 'REPORTING_LINE_CHANGED'
                """);

        assertThat(event.get("actor")).as("who moved him").isEqualTo(personBehind(company.maria));
        assertThat(event.get("subject")).as("who was moved").isEqualTo(personBehind(company.andrei));
        assertThat(event.get("former_manager")).as("who he reported to before").isEqualTo(personBehind(company.ioana));
        assertThat(event.get("new_manager")).as("who he reports to now").isEqualTo(personBehind(company.ionut));
    }

    @Test
    void theDatabaseRefusesAReportingLineEventThatNamesNobody() throws Exception {
        buildTheCompany();
        UUID workspace = jdbc.queryForObject("select id from workspace", UUID.class);

        assertThatThrownBy(() -> jdbc.update(
                        "insert into workspace_event (id, action, actor_user_id, workspace_id, occurred_at)"
                                + " values (?, 'REPORTING_LINE_CHANGED', null, ?, now())",
                        UUID.randomUUID(),
                        workspace))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("workspace_event_reporting_line_names_everyone");
    }

    private String personBehind(String membershipId) {
        return jdbc.queryForObject(
                "select user_id::text from workspace_membership where id = ?::uuid", String.class, membershipId);
    }

    @Test
    void thePeopleBelowSomebodyMoveWithThemAndStillReportToThem() throws Exception {
        Company company = buildTheCompany();

        assertThat(browser.post(
                                "/api/workspace/people/%s/manager".formatted(company.ioana),
                                "{\"proposedManagerId\":\"%s\"}".formatted(company.maria))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(jdbc.queryForObject(
                        "select manager_id from workspace_membership where id = ?::uuid", String.class, company.andrei))
                .as("the subtree travels intact")
                .isEqualTo(company.ioana);
    }

    @Test
    void movingSomebodyDoesNotRewriteTheDayTheyJoined() throws Exception {
        Company company = buildTheCompany();
        String before = jdbc.queryForObject(
                "select joined_at::text from workspace_membership where id = ?::uuid", String.class, company.ioana);

        assertThat(browser.post(
                                "/api/workspace/people/%s/manager".formatted(company.ioana),
                                "{\"proposedManagerId\":\"%s\"}".formatted(company.maria))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(jdbc.queryForObject(
                        "select joined_at::text from workspace_membership where id = ?::uuid",
                        String.class,
                        company.ioana))
                .isEqualTo(before);
    }

    @Test
    void movingSomebodyUnderTheirOwnReportIsRefusedAndNamesThePath() throws Exception {
        Company company = buildTheCompany();

        ResponseEntity<String> refused = browser.post(
                "/api/workspace/people/%s/manager".formatted(company.ionut),
                "{\"proposedManagerId\":\"%s\"}".formatted(company.andrei));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        JsonNode body = json.readTree(refused.getBody());
        assertThat(body.get("code").asText()).isEqualTo("CYCLE");
        assertThat(body.get("details"))
                .as("the path that closes the loop, so the screen can explain it")
                .isNotEmpty();

        assertThat(jdbc.queryForObject(
                        "select manager_id from workspace_membership where id = ?::uuid", String.class, company.ionut))
                .as("a refused move changes nothing")
                .isEqualTo(company.maria);
    }

    @Test
    void movingSomebodyUnderAnEmployeeIsRefused() throws Exception {
        Company company = buildTheCompany();

        ResponseEntity<String> refused = browser.post(
                "/api/workspace/people/%s/manager".formatted(company.ionut),
                "{\"proposedManagerId\":\"%s\"}".formatted(company.elena));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("MANAGER_NOT_ELIGIBLE");
    }

    @Test
    void somebodyWhoseAccessEndedCannotBeMoved() throws Exception {
        Company company = buildTheCompany();
        jdbc.update(
                "update workspace_membership set status = 'DEACTIVATED', deactivated_at = ? where id = ?::uuid",
                OffsetDateTime.now(),
                company.elena);

        ResponseEntity<String> refused = browser.post(
                "/api/workspace/people/%s/manager".formatted(company.elena),
                "{\"proposedManagerId\":\"%s\"}".formatted(company.ionut));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("SUBJECT_INACTIVE");
        assertThat(jdbc.queryForObject(
                        "select manager_id from workspace_membership where id = ?::uuid", String.class, company.elena))
                .as("a refused move changes nothing")
                .isEqualTo(company.maria);
    }

    @Test
    void theOwnerCannotBeGivenAManager() throws Exception {
        Company company = buildTheCompany();

        ResponseEntity<String> refused = browser.post(
                "/api/workspace/people/%s/manager".formatted(company.maria),
                "{\"proposedManagerId\":\"%s\"}".formatted(company.ionut));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("OWNER_HAS_NO_MANAGER");
    }

    @Test
    void proposingTheManagerSomebodyAlreadyHasChangesNothingAndAppendsNoEvent() throws Exception {
        Company company = buildTheCompany();

        ResponseEntity<String> answered = browser.post(
                "/api/workspace/people/%s/manager".formatted(company.ioana),
                "{\"proposedManagerId\":\"%s\"}".formatted(company.ionut));

        assertThat(answered.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(answered.getBody()).get("changed").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject(
                        "select count(*) from workspace_event where action = 'REPORTING_LINE_CHANGED'", Long.class))
                .isZero();
    }

    @Test
    void thePreviewNamesEverybodyWhoWouldMove() throws Exception {
        Company company = buildTheCompany();

        ResponseEntity<String> preview = browser.get("/api/workspace/people/%s/manager/preview?proposedManagerId=%s"
                .formatted(company.ioana, company.maria));

        assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = json.readTree(preview.getBody());
        assertThat(body.get("personName").asText()).isEqualTo("Ioana Radu");
        assertThat(body.get("formerManagerName").asText()).isEqualTo("Ionuț Petrescu");
        assertThat(body.get("newManagerName").asText()).isEqualTo("Maria Ionescu");
        assertThat(body.get("alreadyTheirManager").asBoolean()).isFalse();
        assertThat(body.get("movingWithThem")).hasSize(1);
        assertThat(body.get("movingWithThem").get(0).get("displayName").asText())
                .isEqualTo("Andrei Munteanu");
    }

    @Test
    void aCallerWithoutReportingLineEditIsRefused() throws Exception {
        Company company = buildTheCompany();
        jdbc.update("delete from auth_role_permission where role_name = 'OWNER'"
                + " and permission_name = 'REPORTING_LINE_EDIT'");
        try {
            browser.forget();
            signIn(OWNER_EMAIL);

            ResponseEntity<String> refused = browser.post(
                    "/api/workspace/people/%s/manager".formatted(company.ioana),
                    "{\"proposedManagerId\":\"%s\"}".formatted(company.maria));

            assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("NOT_PERMITTED");
        } finally {
            jdbc.update("insert into auth_role_permission (role_name, permission_name)"
                    + " values ('OWNER', 'REPORTING_LINE_EDIT')");
        }
    }

    private record Company(String maria, String ionut, String ioana, String andrei, String elena) {}

    private Company buildTheCompany() throws Exception {
        registerTheOwnerAndSetUp();
        String maria =
                jdbc.queryForObject("select id::text from workspace_membership where manager_id is null", String.class);

        String ionut = seed("ionut@atelier.ro", "Ionuț Petrescu", "MANAGER", maria);
        String ioana = seed("ioana@atelier.ro", "Ioana Radu", "MANAGER", ionut);
        String andrei = seed("andrei@atelier.ro", "Andrei Munteanu", "EMPLOYEE", ioana);
        String elena = seed("elena@atelier.ro", "Elena Dobre", "EMPLOYEE", maria);
        return new Company(maria, ionut, ioana, andrei, elena);
    }

    private String seed(String email, String displayName, String role, String managerId) {
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
        return membershipId.toString();
    }

    private void signIn(String email) {
        browser.get("/api/auth/session");
        assertThat(browser.post("/api/auth/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
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
