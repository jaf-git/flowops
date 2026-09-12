package com.flowops.process.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.RoundTripClient;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("TASK-SET-DEADLINE-01")
class ProcessUndatedRoundTripTest extends ProcessScenarioTest {
    private Company company;
    private JsonNode instance;

    @BeforeEach
    void aRunSteeredByAnEmployeeAndStartedByTheOwner() throws Exception {
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
    void aStepIsAssignedWithNoDeadlineAndTheTaskIsCreatedUndated() throws Exception {
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");

        ResponseEntity<String> assigned = assignWithNoDeadline(andrei, step(0), company.elena());

        assertThat(assigned.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> row = taskRowFor(json.readTree(assigned.getBody()), 0);
        assertThat(row.get("deadline")).as("nobody was made to guess a date").isNull();
        assertThat(row.get("state")).isEqualTo("CREATED");
        assertThat(row.get("creator_user_id")).isEqualTo(company.maria());
    }

    @Test
    void theAssigneeAcceptsUndatedWorkAndIsRefusedTheStart() throws Exception {
        String task = assignUndatedToElena();
        RoundTripClient elena = signedInBrowser("elena@atelier.ro");

        assertThat(elena.post("/api/tasks/" + task + "/accept", "").getStatusCode())
                .as("accepting undated work is ordinary")
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<String> refused = elena.post("/api/tasks/" + task + "/start", "");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("DEADLINE_REQUIRED_TO_START");

        assertThat(jdbc.queryForObject(
                        "select count(*) from task_phase_timer where task_id = ?::uuid and phase_kind = 'ACTIVE'",
                        Integer.class,
                        task))
                .isZero();
    }

    @Test
    void theAssigneeSetsTheDateAndThenWorkBegins() throws Exception {
        String task = assignUndatedToElena();
        RoundTripClient elena = signedInBrowser("elena@atelier.ro");
        elena.post("/api/tasks/" + task + "/accept", "");

        ResponseEntity<String> set =
                elena.post("/api/tasks/" + task + "/deadline", "{\"deadline\":\"%s\"}".formatted(inThreeDays()));
        assertThat(set.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(elena.post("/api/tasks/" + task + "/start", "").getStatusCode())
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> row = jdbc.queryForMap("select * from task where id = ?::uuid", task);
        assertThat(row.get("state")).isEqualTo("IN_PROGRESS");
        assertThat(row.get("deadline")).isNotNull();
        assertThat(row.get("deadline_set_by"))
                .as("the date is hers, and the row says so")
                .isEqualTo(company.elena());
    }

    @Test
    void nobodyButTheAssigneeMaySetTheDate() throws Exception {
        String task = assignUndatedToElena();
        signedInBrowser("elena@atelier.ro").post("/api/tasks/" + task + "/accept", "");

        ResponseEntity<String> refused = signedInBrowser("ionut@atelier.ro")
                .post("/api/tasks/" + task + "/deadline", "{\"deadline\":\"%s\"}".formatted(inThreeDays()));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("NOT_THE_ASSIGNEE");
    }

    @Test
    void theDateCannotBeSetBeforeTheWorkIsAccepted() throws Exception {
        String task = assignUndatedToElena();

        ResponseEntity<String> refused = signedInBrowser("elena@atelier.ro")
                .post("/api/tasks/" + task + "/deadline", "{\"deadline\":\"%s\"}".formatted(inThreeDays()));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("ILLEGAL_TRANSITION");
    }

    @Test
    void onceWorkHasBegunTheRouteIsAProposalAndTheRefusalSaysSo() throws Exception {
        String task = assignUndatedToElena();
        RoundTripClient elena = signedInBrowser("elena@atelier.ro");
        elena.post("/api/tasks/" + task + "/accept", "");
        elena.post("/api/tasks/" + task + "/deadline", "{\"deadline\":\"%s\"}".formatted(inThreeDays()));
        elena.post("/api/tasks/" + task + "/start", "");

        ResponseEntity<String> refused =
                elena.post("/api/tasks/" + task + "/deadline", "{\"deadline\":\"%s\"}".formatted(inSixDays()));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("USE_A_PROPOSAL_INSTEAD");
    }

    @Test
    void theInstantiatorIsToldWhichDateTheAssigneeChose() throws Exception {
        String task = assignUndatedToElena();
        RoundTripClient elena = signedInBrowser("elena@atelier.ro");
        elena.post("/api/tasks/" + task + "/accept", "");
        elena.post("/api/tasks/" + task + "/deadline", "{\"deadline\":\"%s\"}".formatted(inThreeDays()));

        JsonNode notices = json.readTree(signedInBrowser("maria@atelier.ro")
                        .get("/api/tasks/deadline-notices")
                        .getBody())
                .get("notices");

        assertThat(notices).hasSize(1);
        assertThat(notices.get(0).get("taskId").asText()).isEqualTo(task);
        assertThat(notices.get(0).get("assigneeId").asText())
                .isEqualTo(company.elena().toString());
    }

    @Test
    void acknowledgingClearsTheNoticeAndChangesNothingAboutTheTask() throws Exception {
        String task = assignUndatedToElena();
        RoundTripClient elena = signedInBrowser("elena@atelier.ro");
        elena.post("/api/tasks/" + task + "/accept", "");
        elena.post("/api/tasks/" + task + "/deadline", "{\"deadline\":\"%s\"}".formatted(inThreeDays()));
        RoundTripClient maria = signedInBrowser("maria@atelier.ro");

        assertThat(maria.post("/api/tasks/" + task + "/deadline-notice/acknowledge", "")
                        .getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(json.readTree(maria.get("/api/tasks/deadline-notices").getBody())
                        .get("notices"))
                .isEmpty();
        assertThat(jdbc.queryForMap("select * from task where id = ?::uuid", task)
                        .get("state"))
                .as("acknowledging is not a decision about the work")
                .isEqualTo("ACCEPTED");
    }

    @Test
    void aDateTheCreatorSetRaisesNoNoticeToTheCreator() throws Exception {
        String task = assignUndatedToElena();
        RoundTripClient maria = signedInBrowser("maria@atelier.ro");

        assertThat(maria.exchange(
                                org.springframework.http.HttpMethod.PUT,
                                "/api/tasks/" + task,
                                "{\"deadline\":\"%s\",\"priority\":\"NORMAL\"}".formatted(inThreeDays()))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(json.readTree(maria.get("/api/tasks/deadline-notices").getBody())
                        .get("notices"))
                .isEmpty();
    }

    @Test
    void changingTheDateBringsTheNoticeBackEvenAfterItWasAcknowledged() throws Exception {
        String task = assignUndatedToElena();
        RoundTripClient elena = signedInBrowser("elena@atelier.ro");
        elena.post("/api/tasks/" + task + "/accept", "");
        elena.post("/api/tasks/" + task + "/deadline", "{\"deadline\":\"%s\"}".formatted(inThreeDays()));

        RoundTripClient maria = signedInBrowser("maria@atelier.ro");
        maria.post("/api/tasks/" + task + "/deadline-notice/acknowledge", "");
        assertThat(json.readTree(maria.get("/api/tasks/deadline-notices").getBody())
                        .get("notices"))
                .as("she has seen the first date")
                .isEmpty();

        elena.post("/api/tasks/" + task + "/deadline", "{\"deadline\":\"%s\"}".formatted(inSixDays()));

        assertThat(json.readTree(maria.get("/api/tasks/deadline-notices").getBody())
                        .get("notices"))
                .as("she has not seen this one")
                .hasSize(1);
    }

    @Test
    void acknowledgingTwiceIsNotAnError() throws Exception {
        String task = assignUndatedToElena();
        RoundTripClient elena = signedInBrowser("elena@atelier.ro");
        elena.post("/api/tasks/" + task + "/accept", "");
        elena.post("/api/tasks/" + task + "/deadline", "{\"deadline\":\"%s\"}".formatted(inThreeDays()));
        RoundTripClient maria = signedInBrowser("maria@atelier.ro");

        assertThat(maria.post("/api/tasks/" + task + "/deadline-notice/acknowledge", "")
                        .getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(maria.post("/api/tasks/" + task + "/deadline-notice/acknowledge", "")
                        .getStatusCode())
                .as("nothing left to acknowledge is not a failure")
                .isEqualTo(HttpStatus.NO_CONTENT);
    }

    private String assignUndatedToElena() throws Exception {
        ResponseEntity<String> assigned =
                assignWithNoDeadline(signedInBrowser("andrei@atelier.ro"), step(0), company.elena());
        assertThat(assigned.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json.readTree(assigned.getBody())
                .get("steps")
                .get(0)
                .get("taskId")
                .asText();
    }

    private ResponseEntity<String> assignWithNoDeadline(RoundTripClient who, String step, java.util.UUID assignee) {
        return who.post(
                "/api/process-instances/" + instanceId() + "/steps/" + step + "/assignment",
                "{\"assigneeId\":\"%s\"}".formatted(assignee));
    }

    private Map<String, Object> taskRowFor(JsonNode afterAssignment, int position) {
        return jdbc.queryForMap(
                "select * from task where id = ?::uuid",
                afterAssignment.get("steps").get(position).get("taskId").asText());
    }

    private String instanceId() {
        return instance.get("id").asText();
    }

    private String step(int position) {
        return instance.get("steps").get(position).get("id").asText();
    }

    private static String inThreeDays() {
        return Instant.now().plusSeconds(3 * 86_400).toString();
    }

    private static String inSixDays() {
        return Instant.now().plusSeconds(6 * 86_400).toString();
    }
}
