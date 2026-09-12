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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("PROCESS-ADD-TASK-01")
class ProcessAddTaskRoundTripTest extends ProcessScenarioTest {
    private Company company;
    private JsonNode instance;

    @BeforeEach
    void aRunAndSomeWorkBesideIt() throws Exception {
        company = buildTheCompany();
        JsonNode template = authorOnboarding(browser, "Integrare — adăugare");
        ResponseEntity<String> started = browser.post(
                "/api/process-instances",
                "{\"templateId\":\"%s\",\"name\":\"Integrare — Elena\",\"processOwnerId\":\"%s\"}"
                        .formatted(templateId(template), company.maria()));
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        instance = json.readTree(started.getBody());
    }

    private String instanceId() {
        return instance.get("id").asText();
    }

    private String step(int position) {
        return instance.get("steps").get(position).get("id").asText();
    }

    private static String tomorrow() {
        return java.time.Instant.now().plusSeconds(86_400).toString();
    }

    private String aTaskOfMariasCalled(String title) throws Exception {
        ResponseEntity<String> created = browser.post(
                "/api/tasks",
                "{\"title\":\"%s\",\"description\":\"pregătire\",\"assigneeId\":\"%s\",\"deadline\":\"%s\",\"priority\":\"NORMAL\"}"
                        .formatted(title, company.elena(), tomorrow()));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(created.getBody()).get("id").asText();
    }

    private ResponseEntity<String> attach(RoundTripClient who, String taskId, String... waitsFor) {
        String waits = waitsFor.length == 0 ? "" : "\"" + String.join("\",\"", waitsFor) + "\"";
        return who.post(
                "/api/process-instances/" + instanceId() + "/tasks",
                "{\"taskId\":\"%s\",\"dependsOnStepIds\":[%s]}".formatted(taskId, waits));
    }

    private Map<String, Object> taskRow(String taskId) {
        return jdbc.queryForMap("select * from task where id = ?::uuid", taskId);
    }

    @Test
    void anExistingTaskJoinsTheRunAndNothingAboutItChanges() throws Exception {
        String taskId = aTaskOfMariasCalled("Reconciliază extrasele");
        Map<String, Object> before = taskRow(taskId);

        ResponseEntity<String> added = attach(browser, taskId);

        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> after = taskRow(taskId);
        assertThat(after.get("state")).isEqualTo(before.get("state"));
        assertThat(after.get("assignee_user_id")).isEqualTo(before.get("assignee_user_id"));
        assertThat(after.get("deadline")).isEqualTo(before.get("deadline"));
        assertThat(after.get("title")).isEqualTo("Reconciliază extrasele");

        assertThat(after.get("process_instance_id").toString()).isEqualTo(instanceId());
        assertThat(after.get("instance_step_id")).isNotNull();
    }

    @Test
    void theRunShowsTheAttachedTasksOwnTitleAndItsAssignee() throws Exception {
        String taskId = aTaskOfMariasCalled("Reconciliază extrasele");

        JsonNode run = json.readTree(attach(browser, taskId).getBody());

        JsonNode added = lastStepOf(run);
        assertThat(added.get("title").asText()).isEqualTo("Reconciliază extrasele");
        assertThat(added.get("taskId").asText()).isEqualTo(taskId);
        assertThat(added.get("planned").asBoolean()).isFalse();
        assertThat(added.get("assigneeId").asText()).isEqualTo(company.elena().toString());
        assertThat(added.get("condition").asText()).isEqualTo("ASSIGNED");
        assertThat(added.get("definitionId").isNull()).isTrue();
    }

    @Test
    void anAttachedTaskCanBeMadeToWaitForStepsAlreadyInTheRun() throws Exception {
        String taskId = aTaskOfMariasCalled("Verificare finală");

        JsonNode run = json.readTree(attach(browser, taskId, step(0)).getBody());

        JsonNode added = lastStepOf(run);
        assertThat(added.get("dependsOn").toString()).contains(step(0));

        assertThat(added.get("condition").asText()).isEqualTo("ASSIGNED");
    }

    @Test
    void aTaskAlreadyInARunCannotJoinASecondOne() throws Exception {
        String taskId = aTaskOfMariasCalled("Reconciliază extrasele");
        attach(browser, taskId);

        ResponseEntity<String> again = attach(browser, taskId);

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(again.getBody()).get("code").asText()).isEqualTo("TASK_ALREADY_IN_A_PROCESS");
        assertThat(jdbc.queryForObject(
                        "select count(*) from instance_step where task_id = ?::uuid", Integer.class, taskId))
                .isEqualTo(1);
    }

    @Test
    void theDatabaseRefusesASecondStepOverOneTask() throws Exception {
        String taskId = aTaskOfMariasCalled("Reconciliază extrasele");
        attach(browser, taskId);

        assertThatThrownBySecondInsert(taskId);
    }

    private void assertThatThrownBySecondInsert(String taskId) {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                        """
                        insert into instance_step
                            (id, instance_id, definition_id, origin, title, position, condition, task_id)
                        values (?::uuid, ?::uuid, null, 'ATTACHED', 'copie', 99, 'ASSIGNED', ?::uuid)
                        """,
                        UUID.randomUUID(),
                        UUID.fromString(instanceId()),
                        UUID.fromString(taskId)))
                .hasMessageContaining("instance_step_task_unique_ix");
    }

    @Test
    void aTaskIcannotSeeAndOneThatDoesNotExistAnswerTheSameWay() throws Exception {
        String mariasTask = aTaskOfMariasCalled("Nu e a lui Andrei");
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        String runOfAndreis = runSteeredBy(andrei);

        ResponseEntity<String> outOfScope = andrei.post(
                "/api/process-instances/" + runOfAndreis + "/tasks",
                "{\"taskId\":\"%s\",\"dependsOnStepIds\":[]}".formatted(mariasTask));
        ResponseEntity<String> nonExistent = andrei.post(
                "/api/process-instances/" + runOfAndreis + "/tasks",
                "{\"taskId\":\"%s\",\"dependsOnStepIds\":[]}".formatted(UUID.randomUUID()));

        assertThat(outOfScope.getStatusCode()).isEqualTo(nonExistent.getStatusCode());
        assertThat(outOfScope.getBody()).isEqualTo(nonExistent.getBody());
        assertThat(outOfScope.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aNewTaskCanBeWrittenStraightIntoTheRun() throws Exception {
        ResponseEntity<String> added = browser.post(
                "/api/process-instances/" + instanceId() + "/tasks",
                """
                {"title":"Comandă badge-ul","description":"pentru acces","assigneeId":"%s",\
                "deadline":"%s","priority":"HIGH","dependsOnStepIds":[]}"""
                        .formatted(company.elena(), tomorrow()));

        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode step = lastStepOf(json.readTree(added.getBody()));
        assertThat(step.get("title").asText()).isEqualTo("Comandă badge-ul");
        assertThat(step.get("condition").asText()).isEqualTo("ASSIGNED");

        Map<String, Object> row = taskRow(step.get("taskId").asText());
        assertThat(row.get("state")).isEqualTo("CREATED");
        assertThat(row.get("assignee_user_id")).isEqualTo(company.elena());
        assertThat(row.get("creator_user_id")).isEqualTo(company.maria());
        assertThat(row.get("process_instance_id").toString()).isEqualTo(instanceId());
    }

    @Test
    void writingANewTaskForSomebodyOutsideTheInstantiatorsSubtreeIsRefused() throws Exception {
        RoundTripClient ionut = signedInBrowser("ionut@atelier.ro");
        String run = runStartedBySteeredByAndrei(ionut);
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");

        ResponseEntity<String> refused = andrei.post(
                "/api/process-instances/" + run + "/tasks",
                """
                {"title":"Ceva pentru Elena","assigneeId":"%s","priority":"NORMAL","dependsOnStepIds":[]}"""
                        .formatted(company.elena()));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("ASSIGNEE_OUT_OF_SCOPE");
    }

    @Test
    void writingANewTaskForSomebodyInsideTheInstantiatorsSubtreeSucceeds() throws Exception {
        RoundTripClient ionut = signedInBrowser("ionut@atelier.ro");
        String run = runStartedBySteeredByAndrei(ionut);
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");

        ResponseEntity<String> added = andrei.post(
                "/api/process-instances/" + run + "/tasks",
                """
                {"title":"Ceva pentru Ioana","assigneeId":"%s","priority":"NORMAL","dependsOnStepIds":[]}"""
                        .formatted(company.ioana()));

        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private String runStartedBySteeredByAndrei(RoundTripClient instantiator) throws Exception {
        JsonNode template = authorOnboarding(browser, "Integrare — " + UUID.randomUUID());
        ResponseEntity<String> started = instantiator.post(
                "/api/process-instances",
                "{\"templateId\":\"%s\",\"name\":\"Run\",\"processOwnerId\":\"%s\"}"
                        .formatted(templateId(template), company.andrei()));
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(started.getBody()).get("id").asText();
    }

    @Test
    void aRequestNamingBothDoorsOrNeitherIsRefused() throws Exception {
        ResponseEntity<String> both = browser.post(
                "/api/process-instances/" + instanceId() + "/tasks",
                """
                {"taskId":"%s","title":"și","assigneeId":"%s","dependsOnStepIds":[]}"""
                        .formatted(UUID.randomUUID(), company.elena()));
        ResponseEntity<String> neither =
                browser.post("/api/process-instances/" + instanceId() + "/tasks", "{\"dependsOnStepIds\":[]}");

        assertThat(both.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(neither.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json.readTree(both.getBody()).get("code").asText()).isEqualTo("REQUEST_INVALID");
    }

    @Test
    void aRunsAfterNamingAStepOfAnotherRunIsRefusedAndNothingIsWritten() throws Exception {
        String taskId = aTaskOfMariasCalled("Reconciliază extrasele");
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        String other = runSteeredBy(andrei);
        String foreignStep = json.readTree(
                        browser.get("/api/process-instances/" + other).getBody())
                .get("steps")
                .get(0)
                .get("id")
                .asText();

        ResponseEntity<String> refused = attach(browser, taskId, foreignStep);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("UNKNOWN_STEP");
        assertThat(taskRow(taskId).get("process_instance_id")).isNull();
    }

    @Test
    void thePickerOffersATaskUntilItIsInARunAndThenStops() throws Exception {
        String taskId = aTaskOfMariasCalled("Reconciliază extrasele");

        String before = browser.get("/api/process-instances/" + instanceId() + "/attachable-tasks")
                .getBody();
        attach(browser, taskId);
        String after = browser.get("/api/process-instances/" + instanceId() + "/attachable-tasks")
                .getBody();

        assertThat(before).contains(taskId);
        assertThat(after).doesNotContain(taskId);
    }

    @Test
    void anEmployeeProcessOwnerMayAddToTheirOwnRunAndNotToSomebodyElses() throws Exception {
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        String his = runSteeredBy(andrei);

        ResponseEntity<String> hisOwn = andrei.post(
                "/api/process-instances/" + his + "/tasks",
                """
                {"title":"Notează ce lipsește","assigneeId":"%s","priority":"NORMAL","dependsOnStepIds":[]}"""
                        .formatted(company.andrei()));
        ResponseEntity<String> somebodyElses = andrei.post(
                "/api/process-instances/" + instanceId() + "/tasks",
                """
                {"title":"Nu e runul lui","assigneeId":"%s","priority":"NORMAL","dependsOnStepIds":[]}"""
                        .formatted(company.andrei()));

        assertThat(hisOwn.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(somebodyElses.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void addingATaskAppendsExactlyOneMoreInstanceEvent() throws Exception {
        String taskId = aTaskOfMariasCalled("Reconciliază extrasele");
        int before = eventsOn(instanceId());

        attach(browser, taskId);

        assertThat(eventsOn(instanceId())).isEqualTo(before + 1);
    }

    @Test
    void theCreateDoorCarriesThePriorityThePersonChose() throws Exception {
        ResponseEntity<String> added = browser.post(
                "/api/process-instances/" + instanceId() + "/tasks",
                """
                {"title":"Verifica TVA urgent","assigneeId":"%s","priority":"URGENT","dependsOnStepIds":[]}"""
                        .formatted(company.elena()));

        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String taskId = lastStepOf(json.readTree(added.getBody())).get("taskId").asText();
        assertThat(taskRow(taskId).get("priority")).isEqualTo("URGENT");
    }

    @Test
    void addingAfterARemovalDoesNotReuseAPosition() throws Exception {
        browser.delete("/api/process-instances/" + instanceId() + "/tasks/" + step(0));
        String taskId = aTaskOfMariasCalled("Reconciliaza extrasele");

        attach(browser, taskId);

        List<Integer> positions = jdbc.queryForList(
                "select position from instance_step where instance_id = ?::uuid order by position",
                Integer.class,
                java.util.UUID.fromString(instanceId()));
        assertThat(positions).doesNotHaveDuplicates();
        assertThat(positions).containsExactly(0, 1, 2);
    }

    @Test
    void aTaskCannotBeAddedToAFinishedRun() throws Exception {
        String finished = aRunDrivenToCompletion();
        String taskId = aTaskOfMariasCalled("Prea tarziu");

        ResponseEntity<String> refused = browser.post(
                "/api/process-instances/" + finished + "/tasks",
                "{\"taskId\":\"%s\",\"dependsOnStepIds\":[]}".formatted(taskId));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("INSTANCE_NOT_RUNNING");
    }

    @Test
    void attachingAnAlreadyClosedTaskMakesAClosedStep() throws Exception {
        String taskId = aTaskOfMariasCalled("Deja gata");
        driveToClosed(taskId);

        JsonNode run = json.readTree(attach(browser, taskId).getBody());

        JsonNode added = lastStepOf(run);
        assertThat(added.get("condition").asText()).isEqualTo("CLOSED");
        assertThat(run.get("progress").get("closed").asInt()).isEqualTo(1);
    }

    private String aRunDrivenToCompletion() throws Exception {
        ResponseEntity<String> created = browser.post(
                "/api/process-templates",
                """
                {"name":"Un singur pas %s","overview":null,
                 "steps":[{"taskTemplateId":"%s","expectedDurationHours":1}]}"""
                        .formatted(UUID.randomUUID(), work("Singurul pas")));
        String template = json.readTree(created.getBody()).get("id").asText();
        JsonNode run = json.readTree(browser.post(
                        "/api/process-instances",
                        "{\"templateId\":\"%s\",\"name\":\"Scurt\",\"processOwnerId\":\"%s\"}"
                                .formatted(template, company.maria()))
                .getBody());
        String runId = run.get("id").asText();
        String onlyStep = run.get("steps").get(0).get("id").asText();

        JsonNode assigned = json.readTree(browser.post(
                        "/api/process-instances/" + runId + "/steps/" + onlyStep + "/assignment",
                        "{\"assigneeId\":\"%s\",\"deadline\":\"%s\"}".formatted(company.elena(), tomorrow()))
                .getBody());
        driveToClosed(assigned.get("steps").get(0).get("taskId").asText());
        return runId;
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

    private String runSteeredBy(RoundTripClient who) throws Exception {
        JsonNode template = authorOnboarding(browser, "Integrare — " + UUID.randomUUID());
        ResponseEntity<String> started = browser.post(
                "/api/process-instances",
                "{\"templateId\":\"%s\",\"name\":\"Run\",\"processOwnerId\":\"%s\"}"
                        .formatted(templateId(template), company.andrei()));
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(started.getBody()).get("id").asText();
    }

    private static JsonNode lastStepOf(JsonNode run) {
        JsonNode steps = run.get("steps");
        return steps.get(steps.size() - 1);
    }
}
