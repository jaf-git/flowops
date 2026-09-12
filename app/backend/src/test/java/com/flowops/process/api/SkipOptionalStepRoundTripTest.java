package com.flowops.process.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.RoundTripClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("SOP-METADATA-01")
class SkipOptionalStepRoundTripTest extends ProcessScenarioTest {
    private Company company;
    private JsonNode instance;
    private RoundTripClient ioana;

    @BeforeEach
    void aRunWhoseMiddleStepIsOptional() throws Exception {
        company = buildTheCompany();

        String body = ("{\"name\":\"Livrare campanie\",\"overview\":\"Cum livrăm o campanie\",\"steps\":["
                        + "{\"taskTemplateId\":\"%s\",\"expectedDurationHours\":4},"
                        + "{\"taskTemplateId\":\"%s\",\"expectedDurationHours\":2,"
                        + "\"optional\":true,\"conditionNote\":\"Is the value above 5,000?\"},"
                        + "{\"taskTemplateId\":\"%s\",\"expectedDurationHours\":8}]}")
                .formatted(work("Pregătește materialele"), work("Aprobare financiară"), work("Trimite la client"));

        JsonNode template =
                json.readTree(browser.post("/api/process-templates", body).getBody());
        browser.post(
                "/api/process-templates/" + templateId(template) + "/dependencies",
                edge(stepId(template, 1), stepId(template, 0)));
        browser.post(
                "/api/process-templates/" + templateId(template) + "/dependencies",
                edge(stepId(template, 2), stepId(template, 1)));

        instance = json.readTree(browser.post(
                        "/api/process-instances",
                        "{\"templateId\":\"%s\",\"name\":\"Livrare — Aurora\",\"processOwnerId\":\"%s\"}"
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

    private ResponseEntity<String> skip(RoundTripClient who, int position) {
        return who.post("/api/process-instances/" + instanceId() + "/steps/" + step(position) + "/skip", null);
    }

    @Test
    void theRunCarriesTheQuestionRatherThanReadingItFromTheTemplate() throws Exception {
        JsonNode optional = reread().get("steps").get(1);

        assertThat(optional.get("optional").asBoolean()).isTrue();
        assertThat(optional.get("conditionNote").asText()).isEqualTo("Is the value above 5,000?");
        assertThat(reread().get("steps").get(0).get("optional").asBoolean())
                .as("and a step nobody marked optional is not")
                .isFalse();
    }

    @Test
    void skippingClosesTheStepAndReleasesItsDependent() throws Exception {
        closeFirstStep();
        assertThat(reread().get("steps").get(1).get("condition").asText())
                .as("the optional step is now waiting on its decision")
                .isEqualTo("REACHABLE");
        assertThat(reread().get("steps").get(2).get("condition").asText()).isEqualTo("PENDING");

        assertThat(skip(ioana, 1).getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode after = reread();
        assertThat(after.get("steps").get(1).get("condition").asText())
                .as("it closes, which is how the graph advances at all")
                .isEqualTo("CLOSED");
        assertThat(after.get("steps").get(1).get("skipped").asBoolean())
                .as("and says it was skipped, because CLOSED now means two things")
                .isTrue();
        assertThat(after.get("steps").get(2).get("condition").asText())
                .as("what waited on it is open — the release is the point of closing rather than deleting")
                .isEqualTo("REACHABLE");
    }

    @Test
    void skippingTwiceChangesNothingTheSecondTime() throws Exception {
        closeFirstStep();
        assertThat(skip(ioana, 1).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(skip(ioana, 1).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(reread().get("steps").get(2).get("condition").asText()).isEqualTo("REACHABLE");
    }

    @Test
    void aMandatoryStepIsRefusedAndNamesTheAlternative() throws Exception {
        ResponseEntity<String> refused = skip(ioana, 0);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(refused.getBody()).contains("STEP_NOT_OPTIONAL");
        assertThat(reread().get("steps").get(0).get("condition").asText())
                .as("and it did not move")
                .isEqualTo("REACHABLE");
    }

    @Test
    void aStepStillPendingIsNotYetBeingAskedAbout() throws Exception {
        ResponseEntity<String> refused = skip(ioana, 1);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(refused.getBody()).contains("STEP_NOT_AWAITING_DECISION");
    }

    private void closeFirstStep() throws Exception {
        String task = json.readTree(ioana.post(
                                "/api/process-instances/" + instanceId() + "/steps/" + step(0) + "/assignment",
                                "{\"assigneeId\":\"%s\",\"deadline\":\"%s\"}"
                                        .formatted(
                                                company.elena(),
                                                java.time.Instant.now().plusSeconds(86_400)))
                        .getBody())
                .get("steps")
                .get(0)
                .get("taskId")
                .asText();

        RoundTripClient elena = signedInBrowser("elena@atelier.ro");
        elena.post("/api/tasks/" + task + "/accept", "");
        elena.post("/api/tasks/" + task + "/start", "");
        elena.post("/api/tasks/" + task + "/complete", "{\"note\":\"gata\",\"externalLink\":null}");
        browser.post("/api/tasks/" + task + "/approve", "{\"score\":4,\"comment\":\"bine\"}");
        assertThat(browser.post("/api/tasks/" + task + "/close", "").getStatusCode())
                .as("the graph only advances on Closed, never on Completed — DECISION-PROCESS-REACHABILITY-01")
                .isEqualTo(HttpStatus.OK);
    }
}
