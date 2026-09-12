package com.flowops.process.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.RoundTripClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("PROCESS-START-FROM-TASKS-01")
class ProcessStartFromTasksRoundTripTest extends ProcessScenarioTest {
    private Company company;

    @BeforeEach
    void aCompanyWithWorkAlreadyInIt() throws Exception {
        company = buildTheCompany();
    }

    private static String tomorrow() {
        return java.time.Instant.now().plusSeconds(86_400).toString();
    }

    private String aTaskCalled(String title) throws Exception {
        ResponseEntity<String> created = browser.post(
                "/api/tasks",
                "{\"title\":\"%s\",\"description\":\"pregătire\",\"assigneeId\":\"%s\",\"deadline\":\"%s\",\"priority\":\"NORMAL\"}"
                        .formatted(title, company.elena(), tomorrow()));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(created.getBody()).get("id").asText();
    }

    private ResponseEntity<String> startFrom(RoundTripClient who, String name, UUID steerer, String... taskIds) {
        String tasks = taskIds.length == 0 ? "" : "\"" + String.join("\",\"", taskIds) + "\"";
        return who.post(
                "/api/process-instances/from-tasks",
                "{\"name\":\"%s\",\"processOwnerId\":\"%s\",\"taskIds\":[%s]}".formatted(name, steerer, tasks));
    }

    private Map<String, Object> taskRow(String taskId) {
        return jdbc.queryForMap("select * from task where id = ?::uuid", taskId);
    }

    private int taskCount() {
        return jdbc.queryForObject("select count(*) from task", Integer.class);
    }

    private int instanceCount() {
        return jdbc.queryForObject("select count(*) from process_instance", Integer.class);
    }

    @Test
    void twoTasksBecomeARunInTheOrderGiven() throws Exception {
        String first = aTaskCalled("Reconciliază extrasele");
        String second = aTaskCalled("Verifică facturile");
        int tasksBefore = taskCount();

        ResponseEntity<String> started = startFrom(browser, "Închidere lunară august", company.maria(), first, second);

        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode run = json.readTree(started.getBody());
        assertThat(run.get("steps")).hasSize(2);
        assertThat(run.get("steps").get(0).get("taskId").asText()).isEqualTo(first);
        assertThat(run.get("steps").get(1).get("taskId").asText()).isEqualTo(second);
        assertThat(run.get("steps").get(0).get("title").asText()).isEqualTo("Reconciliază extrasele");

        assertThat(taskCount()).isEqualTo(tasksBefore);
        assertThat(taskRow(first).get("process_instance_id").toString())
                .isEqualTo(run.get("id").asText());
        assertThat(taskRow(second).get("instance_step_id")).isNotNull();
    }

    @Test
    void everyStepIsAttachedAndComesFromNoDefinition() throws Exception {
        String only = aTaskCalled("Singura sarcină");

        JsonNode run = json.readTree(
                startFrom(browser, "Un proces scurt", company.maria(), only).getBody());

        List<Map<String, Object>> rows = jdbc.queryForList(
                "select origin, definition_id from instance_step where instance_id = ?::uuid",
                run.get("id").asText());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("origin")).isEqualTo("ATTACHED");
        assertThat(rows.get(0).get("definition_id")).isNull();
    }

    @Test
    void theRunHasNoTemplate() throws Exception {
        String only = aTaskCalled("Singura sarcină");

        JsonNode run = json.readTree(
                startFrom(browser, "Fără șablon", company.maria(), only).getBody());

        assertThat(run.get("templateId").isNull()).isTrue();
        assertThat(jdbc.queryForMap(
                                "select template_id from process_instance where id = ?::uuid",
                                run.get("id").asText())
                        .get("template_id"))
                .isNull();
    }

    @Test
    void editingATasksDescriptionChangesWhatTheRunShows() throws Exception {
        String only = aTaskCalled("Reconciliază extrasele");
        JsonNode run = json.readTree(
                startFrom(browser, "Închidere", company.maria(), only).getBody());

        assertThat(browser.exchange(
                                HttpMethod.PUT,
                                "/api/tasks/" + only,
                                "{\"deadline\":\"%s\",\"priority\":\"HIGH\",\"description\":\"extrasele BT și ING\"}"
                                        .formatted(tomorrow()))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        JsonNode read = json.readTree(
                browser.get("/api/process-instances/" + run.get("id").asText()).getBody());
        assertThat(read.get("steps").get(0).get("description").asText()).isEqualTo("extrasele BT și ING");
    }

    @Test
    void nothingAboutTheChosenTasksChanges() throws Exception {
        String first = aTaskCalled("Reconciliază extrasele");
        String second = aTaskCalled("Verifică facturile");
        Map<String, Object> before = taskRow(first);

        assertThat(startFrom(browser, "Închidere", company.maria(), first, second)
                        .getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        Map<String, Object> after = taskRow(first);
        assertThat(after.get("state")).isEqualTo(before.get("state"));
        assertThat(after.get("assignee_user_id")).isEqualTo(before.get("assignee_user_id"));
        assertThat(after.get("deadline")).isEqualTo(before.get("deadline"));
        assertThat(after.get("title")).isEqualTo(before.get("title"));
    }

    @Test
    void nothingWaitsForAnythingUntilSomebodySaysSo() throws Exception {
        String first = aTaskCalled("Reconciliază extrasele");
        String second = aTaskCalled("Verifică facturile");

        JsonNode run = json.readTree(
                startFrom(browser, "Închidere", company.maria(), first, second).getBody());

        assertThat(run.get("edges")).isEmpty();
        assertThat(jdbc.queryForObject(
                        "select count(*) from instance_step_dependency where instance_id = ?::uuid",
                        Integer.class,
                        run.get("id").asText()))
                .isZero();
    }

    @Test
    void anAlreadyClosedTaskMakesAClosedStep() throws Exception {
        String done = aTaskCalled("Deja gata");
        driveToClosed(done);
        String other = aTaskCalled("Încă de făcut");

        JsonNode run = json.readTree(
                startFrom(browser, "Închidere", company.maria(), done, other).getBody());

        assertThat(run.get("steps").get(0).get("condition").asText()).isEqualTo("CLOSED");
        assertThat(run.get("progress").get("closed").asInt()).isEqualTo(1);
        assertThat(run.get("progress").get("total").asInt()).isEqualTo(2);
    }

    @Test
    void aStepOverAnOpenTaskIsBornAssignedRatherThanAwaitingAnybody() throws Exception {
        String first = aTaskCalled("Prima");
        String second = aTaskCalled("A doua");

        JsonNode run = json.readTree(
                startFrom(browser, "Închidere", company.maria(), first, second).getBody());

        assertThat(run.get("steps").get(0).get("condition").asText()).isEqualTo("ASSIGNED");
        assertThat(run.get("steps").get(1).get("condition").asText()).isEqualTo("ASSIGNED");

        assertThat(run.get("awaitingAssignment")).isEmpty();
    }

    @Test
    void startingARunAppendsExactlyOneInstanceStartedEvent() throws Exception {
        String only = aTaskCalled("Singura sarcină");

        JsonNode run = json.readTree(
                startFrom(browser, "Închidere", company.maria(), only).getBody());

        assertThat(jdbc.queryForObject(
                        "select count(*) from process_event where instance_id = ?::uuid and action = 'INSTANCE_STARTED'",
                        Integer.class,
                        run.get("id").asText()))
                .isEqualTo(1);
    }

    @Test
    void aRunMadeOnlyOfFinishedWorkIsBornCompleteAndSaysSoInTheLog() throws Exception {
        String first = aTaskCalled("Gata de mult");
        driveToClosed(first);
        String second = aTaskCalled("Și asta gata");
        driveToClosed(second);

        JsonNode run = json.readTree(startFrom(browser, "Închidere iulie", company.maria(), first, second)
                .getBody());

        assertThat(run.get("state").asText()).isEqualTo("COMPLETE");
        assertThat(run.get("progress").get("closed").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "select count(*) from process_event where instance_id = ?::uuid"
                                + " and action = 'INSTANCE_COMPLETED'",
                        Integer.class,
                        run.get("id").asText()))
                .isEqualTo(1);
    }

    @Test
    void refusesASteererWhoHasBeenDeactivated() throws Exception {
        String only = aTaskCalled("Singura sarcină");
        String membership = jdbc.queryForObject(
                "select id::text from workspace_membership where user_id = ?", String.class, company.ionut());
        assertThat(browser.post("/api/workspace/people/" + membership + "/deactivate", "")
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<String> refused = startFrom(browser, "Fără cârmaci", company.ionut(), only);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("PROCESS_OWNER_NOT_ACTIVE");
        assertThat(instanceCount()).isZero();
    }

    @Test
    void aTaskListWithNothingInOneOfItsSlotsIsARequestProblem() throws Exception {
        ResponseEntity<String> refused = browser.post(
                "/api/process-instances/from-tasks",
                "{\"name\":\"Închidere\",\"processOwnerId\":\"%s\",\"taskIds\":[null]}".formatted(company.maria()));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(instanceCount()).isZero();
    }

    @Test
    void aProcessCannotBeStartedWithABlankName() throws Exception {
        String only = aTaskCalled("Singura sarcină");

        ResponseEntity<String> refused = startFrom(browser, "   ", company.maria(), only);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(instanceCount()).isZero();
        assertThat(taskRow(only).get("process_instance_id")).isNull();
    }

    @Test
    void aProcessCannotBeStartedWithNoTasks() throws Exception {
        ResponseEntity<String> refused = startFrom(browser, "Gol", company.maria());

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(instanceCount()).isZero();
    }

    @Test
    void aTaskIcannotSeeAndOneThatDoesNotExistAnswerTheSameWay() throws Exception {
        String mariasTask = aTaskCalled("Nu e a Ioanei");
        RoundTripClient ioana = signedInBrowser("ioana@atelier.ro");

        ResponseEntity<String> outOfScope = startFrom(ioana, "Al Ioanei", company.ioana(), mariasTask);
        ResponseEntity<String> nonExistent =
                startFrom(ioana, "Al Ioanei", company.ioana(), UUID.randomUUID().toString());

        assertThat(outOfScope.getStatusCode()).isEqualTo(nonExistent.getStatusCode());
        assertThat(outOfScope.getBody()).isEqualTo(nonExistent.getBody());
        assertThat(outOfScope.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(instanceCount()).isZero();
    }

    @Test
    void aTaskAlreadyInARunCannotStartASecondOne() throws Exception {
        String taken = aTaskCalled("Deja într-un proces");
        assertThat(startFrom(browser, "Primul", company.maria(), taken).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> refused = startFrom(browser, "Al doilea", company.maria(), taken);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("TASK_ALREADY_IN_A_PROCESS");
        assertThat(instanceCount()).isEqualTo(1);
    }

    @Test
    void namingOneTaskTwiceIsRefused() throws Exception {
        String only = aTaskCalled("O singură dată");

        ResponseEntity<String> refused = startFrom(browser, "De două ori", company.maria(), only, only);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("TASK_ALREADY_IN_A_PROCESS");
        assertThat(instanceCount()).isZero();
    }

    @Test
    void refusesAProcessOwnerWhoIsNotActive() throws Exception {
        String only = aTaskCalled("Singura sarcină");

        ResponseEntity<String> refused = startFrom(browser, "Fără cârmaci", UUID.randomUUID(), only);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("PROCESS_OWNER_NOT_ACTIVE");
        assertThat(instanceCount()).isZero();
        assertThat(taskRow(only).get("process_instance_id")).isNull();
    }

    @Test
    void refusesAnEmployeeStartingARun() throws Exception {
        String only = aTaskCalled("Singura sarcină");
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");

        ResponseEntity<String> refused = startFrom(andrei, "Al lui Andrei", company.andrei(), only);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(instanceCount()).isZero();
    }

    @Test
    void addingRemovingAndReorderingAllWorkOnARunWithNoTemplate() throws Exception {
        String first = aTaskCalled("Prima");
        String second = aTaskCalled("A doua");
        String third = aTaskCalled("A treia");
        JsonNode run = json.readTree(
                startFrom(browser, "Închidere", company.maria(), first, second).getBody());
        String runId = run.get("id").asText();

        ResponseEntity<String> added = browser.post(
                "/api/process-instances/" + runId + "/tasks",
                "{\"taskId\":\"%s\",\"dependsOnStepIds\":[]}".formatted(third));
        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode withThree = json.readTree(added.getBody());
        assertThat(withThree.get("steps")).hasSize(3);

        String stepOfSecond = withThree.get("steps").get(1).get("id").asText();
        String stepOfThird = withThree.get("steps").get(2).get("id").asText();
        String stepOfFirst = withThree.get("steps").get(0).get("id").asText();

        ResponseEntity<String> reordered = browser.exchange(
                HttpMethod.PATCH,
                "/api/process-instances/" + runId + "/tasks/order",
                "{\"stepIds\":[\"%s\",\"%s\",\"%s\"]}".formatted(stepOfThird, stepOfFirst, stepOfSecond));
        assertThat(reordered.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(reordered.getBody())
                        .get("steps")
                        .get(0)
                        .get("taskId")
                        .asText())
                .isEqualTo(third);

        ResponseEntity<String> removed =
                browser.exchange(HttpMethod.DELETE, "/api/process-instances/" + runId + "/tasks/" + stepOfSecond, null);
        assertThat(removed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(removed.getBody()).get("steps")).hasSize(2);

        assertThat(taskRow(second).get("process_instance_id")).isNull();
    }

    @Test
    void thePickerOffersATaskUntilItIsInAProcessAndThenStops() throws Exception {
        String only = aTaskCalled("Liberă deocamdată");

        ResponseEntity<String> before = browser.get("/api/process-instances/attachable-tasks");
        assertThat(before.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(before.getBody()).contains(only);

        startFrom(browser, "Închidere", company.maria(), only);

        assertThat(browser.get("/api/process-instances/attachable-tasks").getBody())
                .doesNotContain(only);
    }

    @Test
    void anEmployeeCannotReadThePicker() throws Exception {
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");

        assertThat(andrei.get("/api/process-instances/attachable-tasks").getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    private void driveToClosed(String taskId) throws Exception {
        RoundTripClient elena = signedInBrowser("elena@atelier.ro");
        elena.post("/api/tasks/" + taskId + "/accept", "");
        elena.post("/api/tasks/" + taskId + "/start", "");
        elena.post("/api/tasks/" + taskId + "/complete", "{\"note\":\"gata\",\"externalLink\":null}");
        browser.post("/api/tasks/" + taskId + "/approve", "{\"score\":4,\"comment\":\"bine\"}");
        assertThat(browser.post("/api/tasks/" + taskId + "/close", "").getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }
}
