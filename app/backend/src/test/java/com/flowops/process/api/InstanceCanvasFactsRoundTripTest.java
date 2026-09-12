package com.flowops.process.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.RoundTripClient;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("PROCESS-VIEW-INSTANCE-01")
@Tag("CANVAS-VIEW-PROCESS-01")
class InstanceCanvasFactsRoundTripTest extends ProcessScenarioTest {
    private Company company;

    @BeforeEach
    void buildTheCompanyFirst() throws Exception {
        company = buildTheCompany();
    }

    private JsonNode chained(String name) throws Exception {
        JsonNode template = authorOnboarding(browser, name);
        browser.post(
                "/api/process-templates/" + templateId(template) + "/dependencies",
                edge(stepId(template, 1), stepId(template, 0)));
        return json.readTree(
                browser.get("/api/process-templates/" + templateId(template)).getBody());
    }

    private JsonNode startAndAssignFirstStep(JsonNode template, UUID to) throws Exception {
        ResponseEntity<String> started = browser.post(
                "/api/process-instances",
                "{\"templateId\":\"%s\",\"name\":\"Integrare — Elena\",\"processOwnerId\":\"%s\"}"
                        .formatted(templateId(template), company.maria()));
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode instance = json.readTree(started.getBody());
        String first = instance.get("steps").get(0).get("id").asText();

        return assignFirstStep(instance, to, "2026-09-30T15:00:00Z");
    }

    private JsonNode assignFirstStep(JsonNode instance, UUID to, String deadline) throws Exception {
        String first = instance.get("steps").get(0).get("id").asText();

        ResponseEntity<String> assigned = browser.post(
                "/api/process-instances/%s/steps/%s/assignment".formatted(instanceId(instance), first),
                "{\"assigneeId\":\"%s\",\"deadline\":\"%s\"}".formatted(to, deadline));
        assertThat(assigned.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode answered = json.readTree(assigned.getBody());
        assertThat(answered.get("steps").get(0).get("assigneeId").asText()).isEqualTo(to.toString());

        return read(instanceId(instance));
    }

    private String instanceId(JsonNode instance) {
        return instance.get("id").asText();
    }

    private JsonNode read(String instance) throws Exception {
        ResponseEntity<String> response = browser.get("/api/process-instances/" + instance);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json.readTree(response.getBody());
    }

    private JsonNode stepAt(JsonNode instance, int position) {
        return instance.get("steps").get(position);
    }

    @Test
    void namesWhoHoldsAnAssignedStep() throws Exception {
        JsonNode instance = startAndAssignFirstStep(chained("Integrare A"), company.andrei());

        JsonNode first = stepAt(instance, 0);

        assertThat(first.get("assigneeId").asText()).isEqualTo(company.andrei().toString());

        assertThat(first.get("assigneeName").asText()).isEqualTo("Andrei Munteanu");
    }

    @Test
    void carriesTheDeadlineTheStepWasGiven() throws Exception {
        JsonNode instance = startAndAssignFirstStep(chained("Integrare B"), company.andrei());

        assertThat(stepAt(instance, 0).get("deadline").asText()).startsWith("2026-09-30T15:00");
    }

    @Test
    void answersWhetherTheWorkIsAtRisk() throws Exception {
        JsonNode comfortable = startAndAssignFirstStep(chained("Integrare C1"), company.andrei());
        assertThat(stepAt(comfortable, 0).get("atRisk").asBoolean()).isFalse();

        JsonNode looming = json.readTree(browser.post(
                        "/api/process-instances",
                        "{\"templateId\":\"%s\",\"name\":\"Integrare C2\",\"processOwnerId\":\"%s\"}"
                                .formatted(templateId(chained("Integrare C2")), company.maria()))
                .getBody());

        JsonNode soon = assignFirstStep(
                looming,
                company.andrei(),
                Instant.now().plus(Duration.ofHours(2)).toString());

        assertThat(stepAt(soon, 0).get("atRisk").asBoolean()).isTrue();
    }

    @Test
    void reportsTimePerPhaseAndNeverAsATotal() throws Exception {
        JsonNode instance = startAndAssignFirstStep(chained("Integrare D"), company.andrei());

        JsonNode phases = stepAt(instance, 0).get("phases");

        assertThat(phases.isArray()).isTrue();
        assertThat(phases).isNotEmpty();
        for (JsonNode phase : phases) {
            assertThat(phase.get("kind").asText()).isNotBlank();
            assertThat(phase.get("seconds").isNumber()).isTrue();
        }

        assertThat(stepAt(instance, 0).has("totalSeconds")).isFalse();
        assertThat(stepAt(instance, 0).has("elapsedSeconds")).isFalse();
    }

    @Test
    void saysOfEveryEdgeWhetherTheThingItWaitsOnHasFinished() throws Exception {
        JsonNode instance = startAndAssignFirstStep(chained("Integrare E"), company.andrei());

        JsonNode edges = instance.get("edges");

        assertThat(edges.isArray()).isTrue();
        assertThat(edges).hasSize(1);

        JsonNode only = edges.get(0);
        assertThat(only.get("from").asText())
                .isEqualTo(stepAt(instance, 0).get("id").asText());
        assertThat(only.get("to").asText())
                .isEqualTo(stepAt(instance, 1).get("id").asText());

        assertThat(only.get("satisfied").asBoolean()).isFalse();
    }

    @Test
    void marksADependencyMetOnceTheStepItWaitsOnHasClosed() throws Exception {
        JsonNode instance = startAndAssignFirstStep(chained("Integrare H"), company.andrei());
        String task = stepAt(instance, 0).get("taskId").asText();

        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        assertThat(andrei.post("/api/tasks/" + task + "/accept", "").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(andrei.post("/api/tasks/" + task + "/start", "").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(andrei.post("/api/tasks/" + task + "/complete", "{\"note\":\"gata\",\"externalLink\":null}")
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(browser.post("/api/tasks/" + task + "/approve", "{\"score\":4,\"comment\":\"bine\"}")
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(browser.post("/api/tasks/" + task + "/close", "").getStatusCode())
                .isEqualTo(HttpStatus.OK);

        JsonNode reread = read(instanceId(instance));

        assertThat(reread.get("edges").get(0).get("satisfied").asBoolean()).isTrue();
    }

    @Test
    void tellsAPendingStepFromAnAssignedOneWithoutInventingWorkForIt() throws Exception {
        JsonNode instance = startAndAssignFirstStep(chained("Integrare F"), company.andrei());

        JsonNode pending = stepAt(instance, 1);

        assertThat(pending.get("condition").asText()).isEqualTo("PENDING");
        assertThat(pending.get("taskId").isNull()).isTrue();
        assertThat(pending.get("assigneeId").isNull()).isTrue();
        assertThat(pending.get("deadline").isNull()).isTrue();
        assertThat(pending.get("phases")).isEmpty();
    }

    @Test
    void namesWhoTaskSaysHoldsTheWorkRatherThanWhoProcessWroteDown() throws Exception {
        JsonNode instance = startAndAssignFirstStep(chained("Integrare G"), company.andrei());
        String step = stepAt(instance, 0).get("id").asText();
        String task = stepAt(instance, 0).get("taskId").asText();

        jdbc.update("update task set assignee_user_id = ?::uuid where id = ?::uuid", company.elena(), task);

        JsonNode reread = read(instanceId(instance));
        JsonNode first = reread.get("steps").get(0);

        assertThat(first.get("assigneeName").asText()).isEqualTo("Elena Dobre");
        assertThat(first.get("assigneeId").asText()).isEqualTo(company.elena().toString());
        assertThat(jdbc.queryForObject(
                        "select assignee_user_id from instance_step where id = ?::uuid", UUID.class, step))
                .isEqualTo(company.andrei());
    }
}
