package com.flowops.workspace.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.auth.domain.model.EmailAddress;
import com.flowops.support.RoundTripClient;
import com.flowops.support.RoundTripTest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("WORKSPACE-CONFIGURE-01")
class WorkspaceSettingsRoundTripTest extends RoundTripTest {
    private static final String OWNER_EMAIL = "maria@atelier.ro";
    private static final String EMPLOYEE_EMAIL = "ioana@atelier.ro";
    private static final String PASSWORD = "a-long-enough-passphrase";
    private static final Pattern PASSCODE_IN_BODY = Pattern.compile("code is (\\S+)\\.");

    @Test
    void anAnonymousCallerCannotReadHowTheBusinessIsMeasured() {
        assertThat(browser.get("/api/workspace/settings").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void anEmployeeIsRefusedBothReadingAndChanging() throws Exception {
        registerTheOwnerAndSetUp();
        seedEmployee();

        RoundTripClient herBrowser = signedInAs(EMPLOYEE_EMAIL);

        ResponseEntity<String> read = herBrowser.get("/api/workspace/settings");
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(json.readTree(read.getBody()).get("code").asText()).isEqualTo("NOT_PERMITTED");

        ResponseEntity<String> write = herBrowser.exchange(HttpMethod.PUT, "/api/workspace/settings", body(Map.of()));
        assertThat(write.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void theOwnerOpensSettingsAndFindsTheShippingDefaults() throws Exception {
        registerTheOwnerAndSetUp();

        ResponseEntity<String> response = browser.get("/api/workspace/settings");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode settings = json.readTree(response.getBody());
        assertThat(settings.get("workingDays")).hasSize(5);
        assertThat(settings.get("atRiskWindowHours").asInt()).isEqualTo(24);
        assertThat(settings.get("quietHoursStart").asText()).startsWith("22:00");
        assertThat(settings.get("quietHoursEnd").asText()).startsWith("06:00");
        assertThat(settings.get("invitationApprovalRequired").asBoolean())
                .as("off by default: the switch is a grant of authority, and shipping it on would grant it")
                .isFalse();
        assertThat(settings.get("changedFields")).isEmpty();
    }

    @Test
    void changingValuesStoresThemAndRecordsEachFieldThatMoved() throws Exception {
        registerTheOwnerAndSetUp();

        ResponseEntity<String> saved = update(Map.of("atRiskWindowHours", 48, "invitationApprovalRequired", true));

        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = json.readTree(saved.getBody());
        assertThat(body.get("atRiskWindowHours").asInt()).isEqualTo(48);
        assertThat(fieldsIn(body.get("changedFields")))
                .containsExactlyInAnyOrder("atRiskWindowHours", "invitationApprovalRequired");

        assertThat(eventCount("SETTINGS_CHANGED")).isEqualTo(1);
        List<Map<String, Object>> changes =
                jdbc.queryForList("select field, old_value, new_value from workspace_settings_change order by field");
        assertThat(changes).hasSize(2);
        assertThat(changes.get(0).get("field")).isEqualTo("atRiskWindowHours");
        assertThat(changes.get(0).get("old_value")).isEqualTo("24");
        assertThat(changes.get(0).get("new_value")).isEqualTo("48");
    }

    @Test
    void theAnalyticalThresholdsSurviveTheWireAndAreRecordedAsChanges() throws Exception {
        registerTheOwnerAndSetUp();

        ResponseEntity<String> saved =
                update(Map.of("closureCoverageThresholdPercent", 75, "templateIdleWindowDays", 120));

        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = json.readTree(saved.getBody());
        assertThat(body.get("closureCoverageThresholdPercent").asInt()).isEqualTo(75);
        assertThat(body.get("templateIdleWindowDays").asInt()).isEqualTo(120);
        assertThat(fieldsIn(body.get("changedFields")))
                .containsExactlyInAnyOrder("closureCoverageThresholdPercent", "templateIdleWindowDays");
    }

    @Test
    void aSubmissionThatOmitsTheThresholdsLeavesThemAlone() throws Exception {
        registerTheOwnerAndSetUp();
        assertThat(update(Map.of("closureCoverageThresholdPercent", 75)).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<String> laterAndUnaware = update(Map.of("atRiskWindowHours", 48));

        assertThat(laterAndUnaware.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = json.readTree(laterAndUnaware.getBody());
        assertThat(body.get("closureCoverageThresholdPercent").asInt())
                .as("the value the owner chose survives a client that does not know the field exists")
                .isEqualTo(75);
        assertThat(fieldsIn(body.get("changedFields")))
                .as("and an untouched field is not recorded as having moved")
                .containsExactly("atRiskWindowHours");
    }

    @Test
    void submittingTheSameValuesAgainWritesNothingAndRecordsNothing() throws Exception {
        registerTheOwnerAndSetUp();
        assertThat(update(Map.of("atRiskWindowHours", 48)).getStatusCode()).isEqualTo(HttpStatus.OK);
        int rowsAfterTheRealChange = settingsRowCount();

        ResponseEntity<String> again = update(Map.of("atRiskWindowHours", 48));

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(again.getBody()).get("changedFields")).isEmpty();
        assertThat(eventCount("SETTINGS_CHANGED")).as("one act, one record").isEqualTo(1);
        assertThat(settingsRowCount())
                .as("no superseding row: ending the old period for no reason is the one way to corrupt the history")
                .isEqualTo(rowsAfterTheRealChange);
    }

    @Test
    void aChangeSupersedesTheOldRowRatherThanEditingIt() throws Exception {
        registerTheOwnerAndSetUp();

        assertThat(update(Map.of("atRiskWindowHours", 48)).getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Map<String, Object>> rows =
                jdbc.queryForList("select at_risk_window_hours, effective_from, effective_to from workspace_settings"
                        + " order by effective_from");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("at_risk_window_hours"))
                .as("the superseded row keeps the value that was true while it applied")
                .isEqualTo(24);
        assertThat(rows.get(0).get("effective_to")).as("and it is closed").isNotNull();
        assertThat(rows.get(1).get("at_risk_window_hours")).isEqualTo(48);
        assertThat(rows.get(1).get("effective_to"))
                .as("exactly one row is in force")
                .isNull();
    }

    @Test
    void quietHoursThatWrapMidnightAreAcceptedOverTheWire() throws Exception {
        registerTheOwnerAndSetUp();

        ResponseEntity<String> saved = update(Map.of("quietHoursStart", "21:30", "quietHoursEnd", "07:15"));

        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = json.readTree(saved.getBody());
        assertThat(body.get("quietHoursStart").asText()).startsWith("21:30");
        assertThat(body.get("quietHoursEnd").asText()).startsWith("07:15");
    }

    @Test
    void anUnorderedLadderIsRefusedAndNamesBothPositions() throws Exception {
        registerTheOwnerAndSetUp();

        ResponseEntity<String> refused = update(Map.of("escalationIntervalsHours", List.of(24, 72, 48)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        JsonNode envelope = json.readTree(refused.getBody());
        assertThat(envelope.get("code").asText()).isEqualTo("ESCALATION_INTERVALS_UNORDERED");
        assertThat(envelope.get("details")).hasSize(2);
        assertThat(settingsRowCount()).as("nothing was stored").isEqualTo(1);
        assertThat(eventCount("SETTINGS_CHANGED")).isZero();
    }

    @Test
    void quietHoursCoveringTheWholeDayAreRefusedWithTheirOwnCode() throws Exception {
        registerTheOwnerAndSetUp();

        ResponseEntity<String> refused = update(Map.of("quietHoursStart", "00:00", "quietHoursEnd", "00:00"));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("QUIET_HOURS_COVER_THE_DAY");
    }

    @Test
    void aWeekWithNoWorkingDayIsRefusedWithItsOwnCode() throws Exception {
        registerTheOwnerAndSetUp();

        ResponseEntity<String> refused = update(Map.of("workingDays", List.of()));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("NO_WORKING_DAYS");
    }

    @Test
    void anAtRiskWindowOfZeroIsRefusedWithItsOwnCode() throws Exception {
        registerTheOwnerAndSetUp();

        ResponseEntity<String> refused = update(Map.of("atRiskWindowHours", 0));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("AT_RISK_WINDOW_INVALID");
    }

    @Test
    void aTimezoneThatIsNotARealZoneIsRefused() throws Exception {
        registerTheOwnerAndSetUp();

        ResponseEntity<String> refused = update(Map.of("timezone", "Europe/Atlantis"));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("TIMEZONE_UNKNOWN");
        assertThat(settingsRowCount()).isEqualTo(1);
    }

    private ResponseEntity<String> update(Map<String, Object> overrides) throws Exception {
        return browser.exchange(HttpMethod.PUT, "/api/workspace/settings", body(overrides));
    }

    private String body(Map<String, Object> overrides) throws Exception {
        Map<String, Object> payload = new java.util.LinkedHashMap<>(Map.of(
                "name", "Atelier Ionescu",
                "use", "WORK",
                "timezone", "Europe/Bucharest",
                "workingDays", List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"),
                "workingHoursStart", "09:00",
                "workingHoursEnd", "17:00",
                "atRiskWindowHours", 24,
                "escalationIntervalsHours", List.of(24, 72, 168),
                "quietHoursStart", "22:00",
                "quietHoursEnd", "06:00"));
        payload.put("invitationApprovalRequired", false);
        payload.putAll(overrides);
        return json.writeValueAsString(payload);
    }

    private List<String> fieldsIn(JsonNode array) {
        return java.util.stream.StreamSupport.stream(array.spliterator(), false)
                .map(JsonNode::asText)
                .toList();
    }

    private int settingsRowCount() {
        return jdbc.queryForObject("select count(*) from workspace_settings", Integer.class);
    }

    private int eventCount(String action) {
        return jdbc.queryForObject("select count(*) from workspace_event where action = ?", Integer.class, action);
    }

    private RoundTripClient signedInAs(String email) {
        RoundTripClient client = new RoundTripClient(rest);
        client.get("/api/auth/session");
        assertThat(client.post("/api/auth/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        return client;
    }

    private void seedEmployee() {
        UUID userId = UUID.randomUUID();
        UUID workspaceId = jdbc.queryForObject("select id from workspace", UUID.class);
        String root =
                jdbc.queryForObject("select id::text from workspace_membership where manager_id is null", String.class);

        jdbc.update(
                "insert into auth_user (id, email, account_state, role_name, created_at, setup_completed, display_name)"
                        + " values (?, ?, 'ACTIVE', 'EMPLOYEE', ?, true, 'Ioana Radu')",
                userId,
                EMPLOYEE_EMAIL,
                OffsetDateTime.now());
        jdbc.update(
                "insert into auth_credential (user_id, password_hash, algorithm, updated_at) values (?, ?, 'bcrypt', ?)",
                userId,
                seededHashOf(PASSWORD),
                OffsetDateTime.now());
        jdbc.update(
                "insert into workspace_membership (id, workspace_id, user_id, status, manager_id, joined_at)"
                        + " values (?, ?, ?, 'ACTIVE', ?::uuid, ?)",
                UUID.randomUUID(),
                workspaceId,
                userId,
                root,
                OffsetDateTime.now());
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
