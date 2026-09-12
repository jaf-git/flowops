package com.flowops.process.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.RoundTripClient;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("PROCESS-ASSIGN-REACHABLE-01")
class ProcessAssignmentRoundTripTest extends ProcessScenarioTest {
    private Company company;
    private JsonNode instance;

    @BeforeEach
    void aRunSteeredByAnEmployee() throws Exception {
        company = buildTheCompany();
        JsonNode template = authorOnboarding(browser, "Integrare angajat nou");
        browser.post(
                "/api/process-templates/" + templateId(template) + "/dependencies",
                edge(stepId(template, 1), stepId(template, 0)));

        ResponseEntity<String> started = browser.post(
                "/api/process-instances",
                "{\"templateId\":\"%s\",\"name\":\"Integrare — Andrei\",\"processOwnerId\":\"%s\"}"
                        .formatted(templateId(template), company.andrei()));
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        instance = json.readTree(started.getBody());
    }

    @Test
    void aTaskCutFromAStepCarriesTheWorkTheStepReferences() throws Exception {
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        ResponseEntity<String> assigned = assign(andrei, step(0), company.andrei());
        assertThat(assigned.getStatusCode()).isEqualTo(HttpStatus.OK);

        String task = json.readTree(assigned.getBody())
                .get("steps")
                .get(0)
                .get("taskId")
                .asText();

        assertThat(jdbc.queryForObject("select template_id from task where id = ?::uuid", UUID.class, task))
                .as("the task names the library entry its step references")
                .isNotNull()
                .isEqualTo(jdbc.queryForObject(
                        "select task_template_id from instance_step where task_id = ?::uuid", UUID.class, task));
    }

    private String instanceId() {
        return instance.get("id").asText();
    }

    private String step(int position) {
        return instance.get("steps").get(position).get("id").asText();
    }

    private ResponseEntity<String> assign(RoundTripClient who, String step, UUID assignee) {
        return who.post(
                "/api/process-instances/" + instanceId() + "/steps/" + step + "/assignment",
                "{\"assigneeId\":\"%s\",\"deadline\":\"%s\"}".formatted(assignee, tomorrow()));
    }

    private static String tomorrow() {
        return java.time.Instant.now().plusSeconds(86_400).toString();
    }

    @Test
    void anEmployeeProcessOwnerAssignsAReachableStepAndATaskAppears() throws Exception {
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");

        ResponseEntity<String> assigned = assign(andrei, step(0), company.elena());

        assertThat(assigned.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode after = json.readTree(assigned.getBody());
        assertThat(after.get("steps").get(0).get("condition").asText()).isEqualTo("ASSIGNED");
        assertThat(after.get("steps").get(0).get("taskId").isNull()).isFalse();

        assertThat(after.get("awaitingAssignment").toString()).doesNotContain(step(0));
        assertThat(after.get("awaitingAssignment").toString()).contains(step(2));
    }

    @Test
    void theTaskNamesTheInstantiatorAsCreatorAndCarriesExactlyTwoProvenanceFacts() throws Exception {
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");

        JsonNode after = json.readTree(assign(andrei, step(0), company.elena()).getBody());
        String taskId = after.get("steps").get(0).get("taskId").asText();

        Map<String, Object> row = jdbc.queryForMap("select * from task where id = ?::uuid", taskId);
        assertThat(row.get("creator_user_id")).isEqualTo(company.maria());
        assertThat(row.get("assignee_user_id")).isEqualTo(company.elena());
        assertThat(row.get("state")).isEqualTo("CREATED");
        assertThat(row.get("process_instance_id").toString()).isEqualTo(instanceId());
        assertThat(row.get("instance_step_id").toString()).isEqualTo(step(0));
    }

    @Test
    void refusesAPendingStepAndNamesTheDependenciesNotYetClosed() throws Exception {
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");

        ResponseEntity<String> refused = assign(andrei, step(1), company.elena());

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode body = json.readTree(refused.getBody());
        assertThat(body.get("code").asText()).isEqualTo("STEP_NOT_REACHABLE");
        assertThat(body.get("details").get(0).get("field").asText()).isEqualTo(step(0));
        assertThat(jdbc.queryForObject("select count(*) from task", Integer.class))
                .isZero();
    }

    @Test
    void refusesAStepThatIsAlreadyAssignedAndCreatesNoSecondTask() throws Exception {
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        assign(andrei, step(0), company.elena());

        ResponseEntity<String> again = assign(andrei, step(0), company.andrei());

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(again.getBody()).get("code").asText()).isEqualTo("ILLEGAL_STEP_TRANSITION");
        assertThat(jdbc.queryForObject("select count(*) from task", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void refusesAnEmployeeWhoNeitherHoldsThePermissionNorSteersThisRun() throws Exception {
        RoundTripClient elena = signedInBrowser("elena@atelier.ro");

        ResponseEntity<String> refused = assign(elena, step(0), company.elena());

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(jdbc.queryForObject("select count(*) from task", Integer.class))
                .isZero();
    }

    @Test
    void aManagerWhoHoldsTheGrantMayAssignARunTheyDoNotSteer() throws Exception {
        RoundTripClient ionut = signedInBrowser("ionut@atelier.ro");

        ResponseEntity<String> assigned = assign(ionut, step(0), company.elena());

        assertThat(assigned.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void thePickerOffersTheInstantiatorsSubtreeRatherThanTheSteerersOwn() throws Exception {
        RoundTripClient ionut = signedInBrowser("ionut@atelier.ro");
        String run = runStartedBy(ionut, company.andrei());
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");

        ResponseEntity<String> offered = andrei.get("/api/process-instances/" + run + "/assignable-people");

        assertThat(offered.getStatusCode()).isEqualTo(HttpStatus.OK);
        String people = json.readTree(offered.getBody()).get("people").toString();
        assertThat(people).contains(company.ionut().toString());
        assertThat(people).contains(company.ioana().toString());
        assertThat(people).contains(company.andrei().toString());
        assertThat(people).doesNotContain(company.elena().toString());
        assertThat(people).doesNotContain(company.maria().toString());
    }

    @Test
    void thePickerRefusesSomebodyWhoCouldNotAssignHere() throws Exception {
        RoundTripClient elena = signedInBrowser("elena@atelier.ro");

        ResponseEntity<String> refused = elena.get("/api/process-instances/" + instanceId() + "/assignable-people");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void thePickerAnswersNothingAboutARunThatDoesNotExist() throws Exception {
        ResponseEntity<String> missing =
                browser.get("/api/process-instances/" + java.util.UUID.randomUUID() + "/assignable-people");

        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(json.readTree(missing.getBody()).get("code").asText()).isEqualTo("INSTANCE_NOT_FOUND");
    }

    @Test
    void readingThePickerAppendsNothingToTheRun() throws Exception {
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        int before = eventsOn(instanceId());

        andrei.get("/api/process-instances/" + instanceId() + "/assignable-people");

        assertThat(eventsOn(instanceId())).isEqualTo(before);
    }

    @Test
    void everyPersonThePickerOffersIsOneTheAssignEndpointAccepts() throws Exception {
        RoundTripClient ionut = signedInBrowser("ionut@atelier.ro");
        String run = runStartedBy(ionut, company.andrei());
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        JsonNode offered = json.readTree(andrei.get("/api/process-instances/" + run + "/assignable-people")
                        .getBody())
                .get("people");
        JsonNode steps = json.readTree(
                        andrei.get("/api/process-instances/" + run).getBody())
                .get("steps");

        assertThat(offered).isNotEmpty();
        assertThat(steps.size()).isGreaterThanOrEqualTo(offered.size());
        for (int position = 0; position < offered.size(); position++) {
            ResponseEntity<String> assigned = andrei.post(
                    "/api/process-instances/" + run + "/steps/"
                            + steps.get(position).get("id").asText() + "/assignment",
                    "{\"assigneeId\":\"%s\",\"deadline\":\"%s\"}"
                            .formatted(offered.get(position).get("id").asText(), tomorrow()));
            assertThat(assigned.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    private String runStartedBy(RoundTripClient who, UUID processOwner) throws Exception {
        JsonNode template = authorOnboarding(browser, "Integrare tehnic");
        ResponseEntity<String> started = who.post(
                "/api/process-instances",
                "{\"templateId\":\"%s\",\"name\":\"Integrare — Elena\",\"processOwnerId\":\"%s\"}"
                        .formatted(templateId(template), processOwner));
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(started.getBody()).get("id").asText();
    }

    @Test
    void refusesAnAssigneeOutsideTheInstantiatorsSubtreeWithTheDocumentedStatus() throws Exception {
        RoundTripClient ionut = signedInBrowser("ionut@atelier.ro");
        String run = runStartedBy(ionut, company.andrei());
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        String step = json.readTree(andrei.get("/api/process-instances/" + run).getBody())
                .get("steps")
                .get(0)
                .get("id")
                .asText();

        ResponseEntity<String> refused = andrei.post(
                "/api/process-instances/" + run + "/steps/" + step + "/assignment",
                "{\"assigneeId\":\"%s\",\"deadline\":\"%s\"}".formatted(company.elena(), tomorrow()));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("ASSIGNEE_OUT_OF_SCOPE");
    }

    @Test
    void assigningAppendsExactlyOneMoreInstanceEvent() throws Exception {
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        int before = jdbc.queryForObject(
                "select count(*) from process_event where instance_id = ?::uuid", Integer.class, instanceId());

        assign(andrei, step(0), company.elena());

        assertThat(jdbc.queryForObject(
                        "select count(*) from process_event where instance_id = ?::uuid", Integer.class, instanceId()))
                .isEqualTo(before + 1);
    }
}
