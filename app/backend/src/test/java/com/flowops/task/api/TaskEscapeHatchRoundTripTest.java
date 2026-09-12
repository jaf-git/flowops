package com.flowops.task.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.RoundTripClient;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("TASK-COMMENT-01")
@Tag("TASK-REASSIGN-01")
@Tag("TASK-OVERRIDE-01")
class TaskEscapeHatchRoundTripTest extends TaskScenarioTest {
    @Test
    void theWorkMovesAndTheHoursStayWithWhoeverAccruedThem() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());

        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        assertThat(andrei.post("/api/tasks/" + task + "/accept", null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(andrei.post("/api/tasks/" + task + "/start", null).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        int phasesBefore = rowsFor("task_phase_timer", task);

        ResponseEntity<String> moved = browser.post(
                "/api/tasks/" + task + "/reassign",
                reassignment(company.elena(), "Andrei este în concediu până luni."));

        assertThat(moved.getStatusCode())
                .as("the owner holds TASK_REASSIGN — V41's grant, not V2's, and this is what proves it ran")
                .isEqualTo(HttpStatus.OK);
        JsonNode body = json.readTree(moved.getBody());
        assertThat(body.get("state").asText())
                .as("acceptance is required again: Elena has not seen this task")
                .isEqualTo("CREATED");
        assertThat(body.get("assigneeName").asText()).isEqualTo("Elena Dobre");

        assertThat(jdbc.queryForObject("select assignee_user_id from task where id = ?::uuid", UUID.class, task))
                .isEqualTo(company.elena());

        assertThat(jdbc.queryForObject(
                        "select count(*) from task_phase_timer where task_id = ?::uuid and phase_kind = 'ACTIVE'"
                                + " and ended_at is not null",
                        Integer.class,
                        task))
                .as("the interval Andrei accrued is closed and still there")
                .isEqualTo(1);
        assertThat(rowsFor("task_phase_timer", task))
                .as("one row closed and one opened, and none deleted or rewritten")
                .isEqualTo(phasesBefore + 1);
        assertThat(jdbc.queryForObject(
                        "select phase_kind from task_phase_timer where task_id = ?::uuid and ended_at is null",
                        String.class,
                        task))
                .as("Elena starts a fresh wait phase, inheriting nothing")
                .isEqualTo("WAIT");

        assertThat(jdbc.queryForObject(
                        "select reason from task_state_transition where task_id = ?::uuid"
                                + " order by occurred_at desc limit 1",
                        String.class,
                        task))
                .isEqualTo("Andrei este în concediu până luni.");
    }

    @Test
    void aManagerCannotMoveWorkToSomebodyOutsideTheirSubtree() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());

        ResponseEntity<String> refused = signedInBrowser("ionut@atelier.ro")
                .post("/api/tasks/" + task + "/reassign", reassignment(company.elena(), "Are mai mult timp."));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("ASSIGNEE_OUT_OF_SCOPE");
        assertThat(jdbc.queryForObject("select assignee_user_id from task where id = ?::uuid", UUID.class, task))
                .as("nothing moved")
                .isEqualTo(company.andrei());
    }

    @Test
    void anEmployeeCannotMoveAnybodysWork() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());

        assertThat(signedInBrowser("andrei@atelier.ro")
                        .post("/api/tasks/" + task + "/reassign", reassignment(company.elena(), "Nu am timp."))
                        .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void movingWorkWithNoReasonIsRefused() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());

        ResponseEntity<String> refused =
                browser.post("/api/tasks/" + task + "/reassign", reassignment(company.elena(), "   "));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("REQUEST_INVALID");
    }

    @Test
    void movingWorkToThePersonWhoAlreadyHasItIsRefusedAndAppendsNothing() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        int transitionsBefore = rowsFor("task_state_transition", task);

        ResponseEntity<String> refused =
                browser.post("/api/tasks/" + task + "/reassign", reassignment(company.andrei(), "Rămâne la el."));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("NOTHING_CHANGED");
        assertThat(rowsFor("task_state_transition", task)).isEqualTo(transitionsBefore);
    }

    @Test
    void theOwnerEndsATaskCreatedInErrorAndTheRecordShowsTheMachineWasBypassed() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());

        ResponseEntity<String> forced =
                browser.post("/api/tasks/" + task + "/override", override("CLOSED", "Comanda a fost anulată."));

        assertThat(forced.getStatusCode())
                .as("V41's grant reached the owner; V2's could not have")
                .isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(forced.getBody()).get("state").asText()).isEqualTo("CLOSED");

        assertThat(jdbc.queryForObject(
                        "select overridden from task_state_transition where task_id = ?::uuid"
                                + " order by occurred_at desc limit 1",
                        Boolean.class,
                        task))
                .as("visibly abnormal on the record, permanently")
                .isTrue();
        assertThat(jdbc.queryForObject(
                        "select count(*) from task_state_transition where task_id = ?::uuid and overridden",
                        Integer.class,
                        task))
                .as("exactly one row is marked; the ordinary moves before it are not")
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select count(*) from task_phase_timer where task_id = ?::uuid and ended_at is null",
                        Integer.class,
                        task))
                .as("Closed opens nothing, so no interval is left accruing on finished work")
                .isZero();
    }

    @Test
    void overrideIsTheOnlyWayBackOutOfClosed() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        browser.post("/api/tasks/" + task + "/override", override("CLOSED", "Închisă din greșeală."));

        ResponseEntity<String> reopened =
                browser.post("/api/tasks/" + task + "/override", override("IN_PROGRESS", "Clientul a revenit."));

        assertThat(reopened.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(reopened.getBody()).get("state").asText()).isEqualTo("IN_PROGRESS");
        assertThat(jdbc.queryForObject(
                        "select phase_kind from task_phase_timer where task_id = ?::uuid and ended_at is null",
                        String.class,
                        task))
                .as("exactly one phase open afterwards, and the right one for the state")
                .isEqualTo("ACTIVE");
    }

    @Test
    void aManagerCannotForceAState() throws Exception {
        buildTheCompany();
        String task = createTaskFor(buildAndrei());

        assertThat(signedInBrowser("ionut@atelier.ro")
                        .post("/api/tasks/" + task + "/override", override("CLOSED", "Nu mai e nevoie."))
                        .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void forcingTheStateTheTaskIsAlreadyInIsRefused() throws Exception {
        buildTheCompany();
        String task = createTaskFor(buildAndrei());

        ResponseEntity<String> refused =
                browser.post("/api/tasks/" + task + "/override", override("CREATED", "Ca să fie sigur."));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("NOTHING_CHANGED");
    }

    @Test
    void theActivityReadsAsOneStoryRatherThanTwoLists() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());

        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        andrei.post("/api/tasks/" + task + "/accept", null);
        andrei.post("/api/tasks/" + task + "/start", null);

        assertThat(andrei.post("/api/tasks/" + task + "/comments", comment("Aștept oferta de la furnizor."))
                        .getStatusCode())
                .as("an employee may comment on their own work with no permission of its own")
                .isEqualTo(HttpStatus.CREATED);
        andrei.post("/api/tasks/" + task + "/block", "{\"reason\":\"Furnizorul nu răspunde.\"}");
        andrei.post("/api/tasks/" + task + "/comments", comment("Am sunat de trei ori."));

        JsonNode activity = json.readTree(
                        browser.get("/api/tasks/" + task + "/activity").getBody())
                .get("entries");

        assertThat(activity).hasSize(6);
        assertThat(kindsOf(activity))
                .containsExactly("TRANSITION", "TRANSITION", "TRANSITION", "COMMENT", "TRANSITION", "COMMENT");
        assertThat(activity.get(3).get("body").asText()).isEqualTo("Aștept oferta de la furnizor.");
        assertThat(activity.get(3).get("actorName").asText()).isEqualTo("Andrei Munteanu");
        assertThat(activity.get(4).get("to").asText()).isEqualTo("BLOCKED");
        assertThat(activity.get(4).get("reason").asText()).isEqualTo("Furnizorul nu răspunde.");
        assertThat(activity.get(4).get("overridden").asBoolean())
                .as("an ordinary move is not marked, or the mark would mean nothing")
                .isFalse();
    }

    @Test
    void aClosedTaskCannotBeCommentedOn() throws Exception {
        buildTheCompany();
        String task = createTaskFor(buildAndrei());
        browser.post("/api/tasks/" + task + "/override", override("CLOSED", "Anulată."));

        ResponseEntity<String> refused = browser.post("/api/tasks/" + task + "/comments", comment("O notă târzie."));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("TASK_IS_CLOSED");
        assertThat(rowsFor("task_comment", task)).isZero();
    }

    @Test
    void somebodyOutsideTheTeamCannotComment() throws Exception {
        buildTheCompany();
        String task = createTaskFor(buildAndrei());

        ResponseEntity<String> refused =
                signedInBrowser("elena@atelier.ro").post("/api/tasks/" + task + "/comments", comment("Un gând."));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("TASK_OUT_OF_SCOPE");
    }

    @Test
    void anEmptyCommentIsRefused() throws Exception {
        buildTheCompany();
        String task = createTaskFor(buildAndrei());

        assertThat(browser.post("/api/tasks/" + task + "/comments", comment("   "))
                        .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rowsFor("task_comment", task)).isZero();
    }

    private UUID buildAndrei() {
        return jdbc.queryForObject("select id from auth_user where email = 'andrei@atelier.ro'", UUID.class);
    }

    private int rowsFor(String table, String task) {
        Integer count =
                jdbc.queryForObject("select count(*) from " + table + " where task_id = ?::uuid", Integer.class, task);
        return count == null ? 0 : count;
    }

    private static java.util.List<String> kindsOf(JsonNode activity) {
        java.util.List<String> kinds = new java.util.ArrayList<>();
        activity.forEach(entry -> kinds.add(entry.get("kind").asText()));
        return kinds;
    }

    private static String reassignment(UUID to, String reason) {
        return "{\"newAssigneeId\":\"%s\",\"reason\":\"%s\"}".formatted(to, reason);
    }

    private static String override(String state, String reason) {
        return "{\"targetState\":\"%s\",\"reason\":\"%s\"}".formatted(state, reason);
    }

    private static String comment(String body) {
        return "{\"body\":\"%s\"}".formatted(body);
    }
}
