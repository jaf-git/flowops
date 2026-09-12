package com.flowops.process.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.RoundTripClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("PROCESS-INSTANTIATE-01")
@Tag("PROCESS-VIEW-INSTANCE-01")
@Tag("PROCESS-EDIT-TEMPLATE-01")
class ProcessInstanceRoundTripTest extends ProcessScenarioTest {
    private Company company;

    @BeforeEach
    void buildTheCompanyFirst() throws Exception {
        company = buildTheCompany();
    }

    private static String tomorrow() {
        return java.time.Instant.now().plusSeconds(86_400).toString();
    }

    private JsonNode chainedTemplate(String name) throws Exception {
        JsonNode template = authorOnboarding(browser, name);
        String id = templateId(template);
        browser.post("/api/process-templates/" + id + "/dependencies", edge(stepId(template, 1), stepId(template, 0)));
        browser.post("/api/process-templates/" + id + "/dependencies", edge(stepId(template, 2), stepId(template, 1)));
        return json.readTree(browser.get("/api/process-templates/" + id).getBody());
    }

    private JsonNode start(RoundTripClient who, JsonNode template, UUID owner, String name) throws Exception {
        ResponseEntity<String> started = who.post(
                "/api/process-instances",
                "{\"templateId\":\"%s\",\"name\":\"%s\",\"processOwnerId\":\"%s\"}"
                        .formatted(templateId(template), name, owner));
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(started.getBody());
    }

    @Test
    void copiesTheWholeTemplateAndMakesOnlyTheEntryStepReachable() throws Exception {
        JsonNode template = chainedTemplate("Integrare angajat nou");

        JsonNode instance = start(browser, template, company.ioana(), "Integrare — Andrei");

        assertThat(instance.get("steps")).hasSize(3);
        assertThat(instance.get("steps").get(0).get("condition").asText()).isEqualTo("REACHABLE");
        assertThat(instance.get("steps").get(1).get("condition").asText()).isEqualTo("PENDING");
        assertThat(instance.get("steps").get(2).get("condition").asText()).isEqualTo("PENDING");
        assertThat(instance.get("state").asText()).isEqualTo("RUNNING");
    }

    @Test
    void copiesEveryEdgeSoTheInstanceCarriesItsOwnGraph() throws Exception {
        JsonNode template = chainedTemplate("Integrare angajat nou");

        JsonNode instance = start(browser, template, company.ioana(), "Integrare — Andrei");

        assertThat(instance.get("steps").get(1).get("dependsOn")).hasSize(1);
        Integer edges = jdbc.queryForObject(
                "select count(*) from instance_step_dependency where instance_id = ?::uuid",
                Integer.class,
                instance.get("id").asText());
        assertThat(edges).isEqualTo(2);
    }

    @Test
    void bothTheListAndTheDetailSayWhichProcessARunIsOf() throws Exception {
        JsonNode onboarding = chainedTemplate("Integrare angajat nou");
        JsonNode offboarding = chainedTemplate("Plecare angajat");

        JsonNode first = start(browser, onboarding, company.ioana(), "Run 1");
        JsonNode second = start(browser, offboarding, company.ioana(), "Run 1");

        assertThat(json.readTree(browser.get("/api/process-instances/"
                                        + first.get("id").asText())
                                .getBody())
                        .get("templateName")
                        .asText())
                .isEqualTo("Integrare angajat nou");
        assertThat(json.readTree(browser.get("/api/process-instances/"
                                        + second.get("id").asText())
                                .getBody())
                        .get("templateName")
                        .asText())
                .isEqualTo("Plecare angajat");

        JsonNode listed =
                json.readTree(browser.get("/api/process-instances").getBody()).get("instances");
        Map<String, String> processOfRun = new HashMap<>();
        for (JsonNode run : listed) {
            processOfRun.put(run.get("id").asText(), run.get("templateName").asText(null));
        }
        assertThat(processOfRun.get(first.get("id").asText())).isEqualTo("Integrare angajat nou");
        assertThat(processOfRun.get(second.get("id").asText())).isEqualTo("Plecare angajat");
    }

    @Test
    void aRunStartedFromTasksNamesNoProcess() throws Exception {
        ResponseEntity<String> created = browser.post(
                "/api/tasks",
                """
                {"title":"Pregătește sala","description":"pregătire",
                 "assigneeId":"%s","deadline":"%s","priority":"NORMAL"}"""
                        .formatted(company.elena(), tomorrow()));
        assertThat(created.getStatusCode())
                .as("the task the run is built from: %s", created.getBody())
                .isEqualTo(HttpStatus.CREATED);
        String task = json.readTree(created.getBody()).get("id").asText();

        ResponseEntity<String> started = browser.post(
                "/api/process-instances/from-tasks",
                "{\"name\":\"Ad-hoc\",\"processOwnerId\":\"%s\",\"taskIds\":[\"%s\"]}"
                        .formatted(company.ioana(), task));
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = json.readTree(started.getBody()).get("id").asText();

        JsonNode detail =
                json.readTree(browser.get("/api/process-instances/" + id).getBody());
        assertThat(detail.get("templateId").isNull()).isTrue();
        assertThat(detail.get("templateName").isNull()).isTrue();
    }

    @Test
    void surfacesTheReachableStepAwaitingAssignmentAsItsOwnField() throws Exception {
        JsonNode template = chainedTemplate("Integrare angajat nou");

        JsonNode instance = start(browser, template, company.ioana(), "Integrare — Andrei");

        assertThat(instance.get("awaitingAssignment")).hasSize(1);
        assertThat(instance.get("awaitingAssignment").get(0).asText())
                .isEqualTo(instance.get("steps").get(0).get("id").asText());
    }

    @Test
    void anEmployeeMayBeNamedProcessOwner() throws Exception {
        JsonNode template = chainedTemplate("Integrare angajat nou");

        JsonNode instance = start(browser, template, company.andrei(), "Integrare — condusă de Andrei");

        assertThat(instance.get("processOwnerId").asText())
                .isEqualTo(company.andrei().toString());
    }

    @Test
    void refusesAProcessOwnerWhoHasBeenDeactivated() throws Exception {
        JsonNode template = chainedTemplate("Integrare angajat nou");
        String membership = jdbc.queryForObject(
                "select id::text from workspace_membership where user_id = ?", String.class, company.elena());
        assertThat(browser.post("/api/workspace/people/" + membership + "/deactivate", "")
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<String> refused = browser.post(
                "/api/process-instances",
                "{\"templateId\":\"%s\",\"name\":\"Fără cârmaci\",\"processOwnerId\":\"%s\"}"
                        .formatted(templateId(template), company.elena()));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("PROCESS_OWNER_NOT_ACTIVE");
        assertThat(jdbc.queryForObject("select count(*) from process_instance", Integer.class))
                .isZero();
    }

    @Test
    void refusesAProcessOwnerWhoIsNotActive() throws Exception {
        JsonNode template = chainedTemplate("Integrare angajat nou");

        ResponseEntity<String> refused = browser.post(
                "/api/process-instances",
                "{\"templateId\":\"%s\",\"name\":\"Fără cârmaci\",\"processOwnerId\":\"%s\"}"
                        .formatted(templateId(template), UUID.randomUUID()));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("PROCESS_OWNER_NOT_ACTIVE");
        assertThat(jdbc.queryForObject("select count(*) from process_instance", Integer.class))
                .isZero();
    }

    @Test
    void refusesAnEmployeeStartingARun() throws Exception {
        JsonNode template = chainedTemplate("Integrare angajat nou");
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");

        ResponseEntity<String> refused = andrei.post(
                "/api/process-instances",
                "{\"templateId\":\"%s\",\"name\":\"Al meu\",\"processOwnerId\":\"%s\"}"
                        .formatted(templateId(template), company.andrei()));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(jdbc.queryForObject("select count(*) from process_instance", Integer.class))
                .isZero();
    }

    @Test
    void editingATemplateLeavesARunningInstanceUntouched() throws Exception {
        JsonNode template = chainedTemplate("Integrare angajat nou");
        JsonNode instance = start(browser, template, company.ioana(), "Integrare — Andrei");
        String instanceId = instance.get("id").asText();
        String before = json.readTree(
                        browser.get("/api/process-instances/" + instanceId).getBody())
                .get("steps")
                .toString();

        ResponseEntity<String> edited = browser.exchange(
                HttpMethod.PATCH,
                "/api/process-templates/" + templateId(template),
                ("{\"overview\":\"Varianta scurtă\",\"steps\":[{\"id\":\"%s\",\"taskTemplateId\":\"%s\","
                                + "\"expectedDurationHours\":null}]}")
                        .formatted(stepId(template, 0), work("Cu totul altceva")));
        assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);

        String after = json.readTree(
                        browser.get("/api/process-instances/" + instanceId).getBody())
                .get("steps")
                .toString();

        assertThat(after).isEqualTo(before);
        assertThat(json.readTree(after)).hasSize(3);
    }

    @Test
    void theProcessOwnerSeesTheRunTheySteerEvenAsAnEmployee() throws Exception {
        JsonNode template = chainedTemplate("Integrare angajat nou");
        JsonNode instance = start(browser, template, company.andrei(), "Integrare — condusă de Andrei");
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");

        ResponseEntity<String> read =
                andrei.get("/api/process-instances/" + instance.get("id").asText());

        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(andrei.get("/api/process-instances").getBody()).get("instances"))
                .hasSize(1);
    }

    @Test
    void aRunSomebodyMayNotSeeAnswersExactlyAsOneThatDoesNotExist() throws Exception {
        JsonNode template = chainedTemplate("Integrare angajat nou");
        JsonNode instance = start(browser, template, company.ioana(), "Integrare — Andrei");
        RoundTripClient elena = signedInBrowser("elena@atelier.ro");

        ResponseEntity<String> hidden =
                elena.get("/api/process-instances/" + instance.get("id").asText());
        ResponseEntity<String> absent = elena.get("/api/process-instances/" + UUID.randomUUID());

        assertThat(hidden.getStatusCode()).isEqualTo(absent.getStatusCode());
        assertThat(hidden.getBody()).isEqualTo(absent.getBody());
    }

    @Test
    void aManagerSeesARunSteeredBySomebodyInTheirSubtree() throws Exception {
        JsonNode template = chainedTemplate("Integrare angajat nou");
        start(browser, template, company.ioana(), "Integrare — Andrei");
        RoundTripClient ionut = signedInBrowser("ionut@atelier.ro");

        JsonNode listed = json.readTree(ionut.get("/api/process-instances").getBody());

        assertThat(listed.get("instances")).hasSize(1);
    }

    @Test
    void namesTheBottleneckAsAStepAndCarriesNoPerPersonAggregate() throws Exception {
        JsonNode template = chainedTemplate("Integrare angajat nou");
        JsonNode instance = start(browser, template, company.ioana(), "Integrare — Andrei");
        String id = instance.get("id").asText();

        browser.post(
                "/api/process-instances/%s/steps/%s/assignment"
                        .formatted(id, instance.get("steps").get(0).get("id").asText()),
                "{\"assigneeId\":\"%s\",\"deadline\":\"2026-09-30T15:00:00Z\"}".formatted(company.andrei()));

        JsonNode read =
                json.readTree(browser.get("/api/process-instances/" + id).getBody());

        assertThat(read.get("bottleneck").get("stepId").asText())
                .isEqualTo(read.get("steps").get(0).get("id").asText());
        assertThat(fieldsOf(read.get("bottleneck"))).containsExactlyInAnyOrder("stepId", "waitedMinutes");
        assertThat(fieldsOf(read.get("progress"))).containsExactlyInAnyOrder("closed", "total");
        assertThat(fieldsOf(read))
                .containsExactlyInAnyOrder(
                        "id",
                        "name",
                        "state",
                        "templateId",
                        "templateName",
                        "processOwnerId",
                        "startedAt",
                        "completedAt",
                        "progress",
                        "steps",
                        "edges",
                        "awaitingAssignment",
                        "bottleneck",
                        "totalDurationMinutes",
                        "abandonedAt",
                        "abandonedReason",
                        "closureNote",
                        "needingAttention");

        for (JsonNode step : read.get("steps")) {
            assertThat(fieldsOf(step))
                    .containsExactlyInAnyOrder(
                            "id",
                            "definitionId",
                            "title",
                            "description",
                            "expectedDurationHours",
                            "position",
                            "condition",
                            "taskId",
                            "planned",
                            "optional",
                            "conditionNote",
                            "skipped",
                            "dependsOn",
                            "taskState",
                            "assigneeId",
                            "assigneeName",
                            "deadline",
                            "atRisk",
                            "phases",
                            "blockedReason");
        }

        assertThat(read.get("steps").get(0).get("assigneeName").asText()).isEqualTo("Andrei Munteanu");
    }

    private static List<String> fieldsOf(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    @Test
    void reportsProgressAsClosedAgainstTotal() throws Exception {
        JsonNode template = chainedTemplate("Integrare angajat nou");
        JsonNode instance = start(browser, template, company.ioana(), "Integrare — Andrei");

        assertThat(instance.get("progress").get("closed").asInt()).isZero();
        assertThat(instance.get("progress").get("total").asInt()).isEqualTo(3);
    }

    @Test
    void viewingAnInstanceNotifiesNobody() throws Exception {
        JsonNode template = chainedTemplate("Integrare angajat nou");
        JsonNode instance = start(browser, template, company.ioana(), "Integrare — Andrei");
        int eventsAfterStarting = jdbc.queryForObject(
                "select count(*) from process_event where instance_id = ?::uuid",
                Integer.class,
                instance.get("id").asText());

        browser.get("/api/process-instances/" + instance.get("id").asText());
        browser.get("/api/process-instances");

        assertThat(jdbc.queryForObject(
                        "select count(*) from process_event where instance_id = ?::uuid",
                        Integer.class,
                        instance.get("id").asText()))
                .isEqualTo(eventsAfterStarting);
    }

    @Test
    void startingARunAppendsExactlyOneInstanceEvent() throws Exception {
        JsonNode template = chainedTemplate("Integrare angajat nou");

        JsonNode instance = start(browser, template, company.ioana(), "Integrare — Andrei");

        assertThat(jdbc.queryForObject(
                        "select count(*) from process_event where instance_id = ?::uuid",
                        Integer.class,
                        instance.get("id").asText()))
                .isEqualTo(1);
    }
}
