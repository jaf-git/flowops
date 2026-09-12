package com.flowops.process.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.RoundTripClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("PROCESS-REMOVE-TASK-01")
class ProcessEditInstanceRoundTripTest extends ProcessScenarioTest {
    private Company company;
    private JsonNode instance;

    @BeforeEach
    void aChainedRun() throws Exception {
        company = buildTheCompany();
        JsonNode template = authorOnboarding(browser, "Integrare — editare");
        browser.post(
                "/api/process-templates/" + templateId(template) + "/dependencies",
                edge(stepId(template, 1), stepId(template, 0)));

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

    private JsonNode readRun() throws Exception {
        ResponseEntity<String> read = browser.get("/api/process-instances/" + instanceId());
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json.readTree(read.getBody());
    }

    private static JsonNode stepById(JsonNode run, String id) {
        for (JsonNode step : run.get("steps")) {
            if (step.get("id").asText().equals(id)) {
                return step;
            }
        }
        throw new AssertionError("no step " + id + " in the run");
    }

    private String assignFirstStep() throws Exception {
        ResponseEntity<String> assigned = browser.post(
                "/api/process-instances/" + instanceId() + "/steps/" + step(0) + "/assignment",
                "{\"assigneeId\":\"%s\",\"deadline\":\"%s\"}"
                        .formatted(company.elena(), java.time.Instant.now().plusSeconds(86_400)));
        assertThat(assigned.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json.readTree(assigned.getBody())
                .get("steps")
                .get(0)
                .get("taskId")
                .asText();
    }

    @Test
    void removingAStepOpensWhatWaitedForItAndTheTaskSurvives() throws Exception {
        String taskId = assignFirstStep();
        Map<String, Object> before = jdbc.queryForMap("select * from task where id = ?::uuid", taskId);
        assertThat(stepById(readRun(), step(1)).get("condition").asText()).isEqualTo("PENDING");

        ResponseEntity<String> removed = browser.delete("/api/process-instances/" + instanceId() + "/tasks/" + step(0));

        assertThat(removed.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(stepById(readRun(), step(1)).get("condition").asText()).isEqualTo("REACHABLE");

        Map<String, Object> after = jdbc.queryForMap("select * from task where id = ?::uuid", taskId);
        assertThat(after.get("state")).isEqualTo(before.get("state"));
        assertThat(after.get("assignee_user_id")).isEqualTo(before.get("assignee_user_id"));
        assertThat(after.get("deadline")).isEqualTo(before.get("deadline"));
        assertThat(after.get("process_instance_id")).isNull();
        assertThat(after.get("instance_step_id")).isNull();
    }

    @Test
    void removingAStepTakesEveryEdgeTouchingItAway() throws Exception {
        browser.delete("/api/process-instances/" + instanceId() + "/tasks/" + step(0));

        JsonNode run = readRun();
        assertThat(run.get("steps")).hasSize(2);
        assertThat(run.get("edges").toString()).doesNotContain(step(0));
        assertThat(jdbc.queryForObject(
                        "select count(*) from instance_step_dependency where dependent_step_id = ?::uuid"
                                + " or depends_on_step_id = ?::uuid",
                        Integer.class,
                        UUID.fromString(step(0)),
                        UUID.fromString(step(0))))
                .isZero();
    }

    @Test
    void theLastStepOfARunCannotBeRemoved() throws Exception {
        browser.delete("/api/process-instances/" + instanceId() + "/tasks/" + step(0));
        browser.delete("/api/process-instances/" + instanceId() + "/tasks/" + step(1));

        ResponseEntity<String> refused = browser.delete("/api/process-instances/" + instanceId() + "/tasks/" + step(2));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("INSTANCE_NEEDS_A_TASK");
        assertThat(readRun().get("steps")).hasSize(1);
    }

    @Test
    void reorderingChangesTheOrderTheRunIsReadIn() throws Exception {
        List<String> reversed = List.of(step(2), step(1), step(0));

        ResponseEntity<String> reordered = browser.exchange(
                HttpMethod.PATCH,
                "/api/process-instances/" + instanceId() + "/tasks/order",
                "{\"stepIds\":[\"%s\"]}".formatted(String.join("\",\"", reversed)));

        assertThat(reordered.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(idsInOrder(readRun())).containsExactlyElementsOf(reversed);
    }

    @Test
    void reorderingChangesNoConditionAndNoEdge() throws Exception {
        JsonNode before = readRun();

        browser.exchange(
                HttpMethod.PATCH,
                "/api/process-instances/" + instanceId() + "/tasks/order",
                "{\"stepIds\":[\"%s\"]}".formatted(String.join("\",\"", List.of(step(2), step(1), step(0)))));

        JsonNode after = readRun();
        for (String id : idsInOrder(before)) {
            assertThat(stepById(after, id).get("condition").asText())
                    .isEqualTo(stepById(before, id).get("condition").asText());
        }
        assertThat(after.get("edges").toString()).isEqualTo(before.get("edges").toString());
    }

    @Test
    void anOrderThatIsNotExactlyThisRunsStepsIsRefusedWhole() throws Exception {
        List<String> before = idsInOrder(readRun());

        ResponseEntity<String> refused = browser.exchange(
                HttpMethod.PATCH,
                "/api/process-instances/" + instanceId() + "/tasks/order",
                "{\"stepIds\":[\"%s\",\"%s\"]}".formatted(step(2), step(1)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("UNKNOWN_STEP");
        assertThat(idsInOrder(readRun())).containsExactlyElementsOf(before);
    }

    @Test
    void drawingADependencyOntoAnUnheldReachableStepReturnsItToPending() throws Exception {
        assertThat(stepById(readRun(), step(2)).get("condition").asText()).isEqualTo("REACHABLE");

        ResponseEntity<String> drawn =
                browser.post("/api/process-instances/" + instanceId() + "/dependencies", edge(step(2), step(0)));

        assertThat(drawn.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stepById(readRun(), step(2)).get("condition").asText()).isEqualTo("PENDING");
    }

    @Test
    void drawingADependencyOntoAnAssignedStepDoesNotMoveIt() throws Exception {
        assignFirstStep();

        browser.post("/api/process-instances/" + instanceId() + "/dependencies", edge(step(0), step(2)));

        assertThat(stepById(readRun(), step(0)).get("condition").asText()).isEqualTo("ASSIGNED");
    }

    @Test
    void anEdgeThatWouldCloseACycleIsRefusedNamingIt() throws Exception {
        ResponseEntity<String> refused =
                browser.post("/api/process-instances/" + instanceId() + "/dependencies", edge(step(0), step(1)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode body = json.readTree(refused.getBody());
        assertThat(body.get("code").asText()).isEqualTo("GRAPH_CYCLE");
        assertThat(body.get("details").toString()).contains(step(0));
    }

    @Test
    void erasingADependencyMakesThePendingStepReachable() throws Exception {
        assertThat(stepById(readRun(), step(1)).get("condition").asText()).isEqualTo("PENDING");

        ResponseEntity<String> erased =
                browser.delete("/api/process-instances/" + instanceId() + "/dependencies/" + step(1) + "/" + step(0));

        assertThat(erased.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stepById(readRun(), step(1)).get("condition").asText()).isEqualTo("REACHABLE");
    }

    @Test
    void drawingTheSameEdgeTwiceAppendsNoSecondEvent() throws Exception {
        browser.post("/api/process-instances/" + instanceId() + "/dependencies", edge(step(2), step(0)));
        int after = eventsOn(instanceId());

        browser.post("/api/process-instances/" + instanceId() + "/dependencies", edge(step(2), step(0)));

        assertThat(eventsOn(instanceId())).isEqualTo(after);
    }

    @Test
    void anEmployeeWhoDoesNotSteerThisRunCannotEditIt() throws Exception {
        RoundTripClient elena = signedInBrowser("elena@atelier.ro");

        ResponseEntity<String> refused = elena.delete("/api/process-instances/" + instanceId() + "/tasks/" + step(0));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(readRun().get("steps")).hasSize(3);
    }

    @Test
    void aStepOfAnotherRunCannotBeRemovedFromThisOne() throws Exception {
        String foreign = aSecondRunsFirstStep();

        ResponseEntity<String> refused = browser.delete("/api/process-instances/" + instanceId() + "/tasks/" + foreign);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("UNKNOWN_STEP");
        assertThat(readRun().get("steps")).hasSize(3);
    }

    @Test
    void erasingAnEdgeNamingAnotherRunsStepIsRefusedRatherThanIgnored() throws Exception {
        String foreign = aSecondRunsFirstStep();

        ResponseEntity<String> refused =
                browser.delete("/api/process-instances/" + instanceId() + "/dependencies/" + foreign + "/" + step(0));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("UNKNOWN_STEP");
    }

    @Test
    void removingAStepLeavesThePositionsContiguous() throws Exception {
        browser.delete("/api/process-instances/" + instanceId() + "/tasks/" + step(0));

        List<Integer> positions = jdbc.queryForList(
                "select position from instance_step where instance_id = ?::uuid order by position",
                Integer.class,
                UUID.fromString(instanceId()));
        assertThat(positions).containsExactly(0, 1);
    }

    private String aSecondRunsFirstStep() throws Exception {
        JsonNode template = authorOnboarding(browser, "Alt proces " + UUID.randomUUID());
        ResponseEntity<String> started = browser.post(
                "/api/process-instances",
                "{\"templateId\":\"%s\",\"name\":\"Alt run\",\"processOwnerId\":\"%s\"}"
                        .formatted(templateId(template), company.maria()));
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(started.getBody()).get("steps").get(0).get("id").asText();
    }

    private static List<String> idsInOrder(JsonNode run) {
        List<String> ids = new ArrayList<>();
        for (JsonNode step : run.get("steps")) {
            ids.add(step.get("id").asText());
        }
        return ids;
    }
}
