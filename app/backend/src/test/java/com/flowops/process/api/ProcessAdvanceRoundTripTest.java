package com.flowops.process.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.RoundTripClient;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("PROCESS-ASSIGN-REACHABLE-01")
class ProcessAdvanceRoundTripTest extends ProcessScenarioTest {
    private Company company;
    private JsonNode instance;
    private RoundTripClient ioana;

    @BeforeEach
    void aRunWithTwoChainedSteps() throws Exception {
        company = buildTheCompany();
        JsonNode template = authorOnboarding(browser, "Integrare angajat nou");
        browser.post(
                "/api/process-templates/" + templateId(template) + "/dependencies",
                edge(stepId(template, 1), stepId(template, 0)));

        instance = json.readTree(browser.post(
                        "/api/process-instances",
                        "{\"templateId\":\"%s\",\"name\":\"Integrare — Andrei\",\"processOwnerId\":\"%s\"}"
                                .formatted(templateId(template), company.ioana()))
                .getBody());
        ioana = signedInBrowser("ioana@atelier.ro");
    }

    private String instanceId() {
        return instance.get("id").asText();
    }

    private String step(int position) {
        return instance.get("steps").get(position).get("id").asText();
    }

    private JsonNode reread() throws Exception {
        return json.readTree(
                browser.get("/api/process-instances/" + instanceId()).getBody());
    }

    private String assignAndGetTask(int position, UUID assignee) throws Exception {
        ResponseEntity<String> assigned = ioana.post(
                "/api/process-instances/" + instanceId() + "/steps/" + step(position) + "/assignment",
                "{\"assigneeId\":\"%s\",\"deadline\":\"%s\"}"
                        .formatted(assignee, Instant.now().plusSeconds(86_400)));
        assertThat(assigned.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json.readTree(assigned.getBody())
                .get("steps")
                .get(position)
                .get("taskId")
                .asText();
    }

    private void carryToClosed(String task) {
        RoundTripClient elena = signedInBrowser("elena@atelier.ro");
        ResponseEntity<String> accepted = elena.post("/api/tasks/" + task + "/accept", "");
        assertThat(accepted.getStatusCode())
                .as("accept said: %s", accepted.getBody())
                .isEqualTo(HttpStatus.OK);
        assertThat(elena.post("/api/tasks/" + task + "/start", "").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(elena.post("/api/tasks/" + task + "/complete", "{\"note\":\"gata\",\"externalLink\":null}")
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(browser.post("/api/tasks/" + task + "/approve", "{\"score\":4,\"comment\":\"bine\"}")
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(browser.post("/api/tasks/" + task + "/close", "").getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void closingAStepOpensTheOneThatWasWaitingForIt() throws Exception {
        String task = assignAndGetTask(0, company.elena());

        carryToClosed(task);

        JsonNode after = reread();
        assertThat(after.get("steps").get(0).get("condition").asText()).isEqualTo("CLOSED");
        assertThat(after.get("steps").get(1).get("condition").asText()).isEqualTo("REACHABLE");
        assertThat(after.get("awaitingAssignment").toString()).contains(step(1));
        assertThat(after.get("progress").get("closed").asInt()).isEqualTo(1);
    }

    @Test
    void completedWorkDoesNotOpenTheNextStep() throws Exception {
        String task = assignAndGetTask(0, company.elena());
        RoundTripClient elena = signedInBrowser("elena@atelier.ro");
        elena.post("/api/tasks/" + task + "/accept", "");
        elena.post("/api/tasks/" + task + "/start", "");

        assertThat(elena.post("/api/tasks/" + task + "/complete", "{\"note\":\"gata\",\"externalLink\":null}")
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        JsonNode after = reread();
        assertThat(after.get("steps").get(0).get("condition").asText()).isEqualTo("ASSIGNED");
        assertThat(after.get("steps").get(1).get("condition").asText()).isEqualTo("PENDING");
        assertThat(after.get("progress").get("closed").asInt()).isZero();
    }

    @Test
    void aDeclinedTaskHandsItsStepBackToTheProcessOwner() throws Exception {
        String task = assignAndGetTask(0, company.elena());
        RoundTripClient elena = signedInBrowser("elena@atelier.ro");

        ResponseEntity<String> rejected =
                elena.post("/api/tasks/" + task + "/reject", "{\"reason\":\"nu am acces la depozit\"}");
        assertThat(rejected.getStatusCode())
                .as("reject said: %s", rejected.getBody())
                .isEqualTo(HttpStatus.OK);

        JsonNode after = reread();
        assertThat(after.get("steps").get(0).get("condition").asText()).isEqualTo("REACHABLE");
        assertThat(after.get("steps").get(0).get("taskId").isNull()).isTrue();
        assertThat(after.get("awaitingAssignment").toString()).contains(step(0));
    }

    @Test
    void closingEveryStepCompletesTheRun() throws Exception {
        carryToClosed(assignAndGetTask(0, company.elena()));
        instance = reread();
        carryToClosed(assignAndGetTask(1, company.elena()));
        instance = reread();
        carryToClosed(assignAndGetTask(2, company.elena()));

        JsonNode after = reread();
        assertThat(after.get("state").asText()).isEqualTo("COMPLETE");
        assertThat(after.get("progress").get("closed").asInt()).isEqualTo(3);
        assertThat(after.get("completedAt").isNull()).isFalse();
        assertThat(after.get("awaitingAssignment")).isEmpty();
        assertThat(after.get("bottleneck").isNull()).isTrue();
    }

    @Test
    void aBlockedStepShowsTheReasonTheAssigneeGave() throws Exception {
        String task = assignAndGetTask(0, company.elena());
        RoundTripClient elena = signedInBrowser("elena@atelier.ro");
        elena.post("/api/tasks/" + task + "/accept", "");
        elena.post("/api/tasks/" + task + "/start", "");

        assertThat(elena.post("/api/tasks/" + task + "/block", "{\"reason\":\"nu am acces la depozit\"}")
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        JsonNode step = reread().get("steps").get(0);
        assertThat(step.get("taskState").asText()).isEqualTo("BLOCKED");
        assertThat(step.get("blockedReason").asText()).isEqualTo("nu am acces la depozit");
    }

    @Test
    void aStepWhoseBlockWasLiftedShowsNoReason() throws Exception {
        String task = assignAndGetTask(0, company.elena());
        RoundTripClient elena = signedInBrowser("elena@atelier.ro");
        elena.post("/api/tasks/" + task + "/accept", "");
        elena.post("/api/tasks/" + task + "/start", "");
        elena.post("/api/tasks/" + task + "/block", "{\"reason\":\"nu am acces la depozit\"}");

        assertThat(elena.post("/api/tasks/" + task + "/unblock", "{\"note\":\"am primit cheia\"}")
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        JsonNode step = reread().get("steps").get(0);
        assertThat(step.get("taskState").asText()).isEqualTo("IN_PROGRESS");
        assertThat(step.get("blockedReason").isNull()).isTrue();
    }

    @Test
    void somebodyHoldingATaskInARunCanSeeIt() throws Exception {
        assignAndGetTask(0, company.elena());
        RoundTripClient elena = signedInBrowser("elena@atelier.ro");

        ResponseEntity<String> read = elena.get("/api/process-instances/" + instanceId());

        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(elena.get("/api/process-instances").getBody()).get("instances"))
                .hasSize(1);
    }

    @Test
    void merelyHavingStartedARunIsNotVisibility() throws Exception {
        RoundTripClient ionut = signedInBrowser("ionut@atelier.ro");
        JsonNode template = authorOnboarding(ionut, "Recepție marfă");
        JsonNode his = json.readTree(ionut.post(
                        "/api/process-instances",
                        "{\"templateId\":\"%s\",\"name\":\"Recepție — martie\",\"processOwnerId\":\"%s\"}"
                                .formatted(templateId(template), company.elena()))
                .getBody());

        ResponseEntity<String> read =
                ionut.get("/api/process-instances/" + his.get("id").asText());

        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void anOrdinaryTaskMovingChangesNoInstance() throws Exception {
        String ordinary = json.readTree(browser.post(
                                "/api/tasks",
                                ("{\"title\":\"Comandă tonerul\",\"description\":null,\"assigneeId\":\"%s\","
                                                + "\"deadline\":\"%s\",\"priority\":\"NORMAL\"}")
                                        .formatted(
                                                company.elena(), Instant.now().plusSeconds(86_400)))
                        .getBody())
                .get("id")
                .asText();
        int eventsBefore = jdbc.queryForObject("select count(*) from process_event", Integer.class);

        RoundTripClient elena = signedInBrowser("elena@atelier.ro");
        assertThat(elena.post("/api/tasks/" + ordinary + "/accept", "").getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(jdbc.queryForObject("select count(*) from process_event", Integer.class))
                .isEqualTo(eventsBefore);
    }
}
