package com.flowops.process.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.RoundTripClient;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("PROCESS-RETIRE-TEMPLATE-01")
@Tag("PROCESS-ABANDON-INSTANCE-01")
class ProcessEndingRoundTripTest extends ProcessScenarioTest {
    @Test
    void aRetiredTemplateLeavesTheLibraryAndTheRunsCutFromItKeepWorking() throws Exception {
        Company company = buildTheCompany();
        JsonNode template = authorOnboarding(browser, "Integrare colegi");
        String templateId = templateId(template);
        String instance = startARun(templateId, company.ionut());

        ResponseEntity<String> retired = browser.post("/api/process-templates/" + templateId + "/retirement", null);

        assertThat(retired.getStatusCode())
                .as("the owner holds PROCESS_TEMPLATE_RETIRE — V42's grant, not V2's, and this is what proves it")
                .isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(retired.getBody()).get("active").asBoolean()).isFalse();

        JsonNode library =
                json.readTree(browser.get("/api/process-templates").getBody()).get("templates");
        assertThat(namesIn(library)).doesNotContain("Integrare colegi");

        JsonNode run =
                json.readTree(browser.get("/api/process-instances/" + instance).getBody());
        assertThat(run.get("state").asText()).isEqualTo("RUNNING");
        assertThat(run.get("templateId").asText()).isEqualTo(templateId);
        assertThat(run.get("steps")).hasSize(3);
    }

    @Test
    void aRetiredTemplateCannotBeStarted() throws Exception {
        Company company = buildTheCompany();
        String templateId = templateId(authorOnboarding(browser, "Integrare colegi"));
        browser.post("/api/process-templates/" + templateId + "/retirement", null);

        ResponseEntity<String> refused = browser.post(
                "/api/process-instances",
                "{\"templateId\":\"%s\",\"name\":\"Prea târziu\",\"processOwnerId\":\"%s\"}"
                        .formatted(templateId, company.ionut()));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("TEMPLATE_IS_RETIRED");
    }

    @Test
    void aManagerCannotRetireATemplateSomebodyElseWrote() throws Exception {
        buildTheCompany();
        String templateId = templateId(authorOnboarding(browser, "Integrare colegi"));

        ResponseEntity<String> refused =
                signedInBrowser("ionut@atelier.ro").post("/api/process-templates/" + templateId + "/retirement", null);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("NOT_THE_AUTHOR");
        assertThat(jdbc.queryForObject(
                        "select active from process_template where id = ?::uuid", Boolean.class, templateId))
                .isTrue();
    }

    @Test
    void retiringTwiceIsRefusedAndAppendsNothing() throws Exception {
        buildTheCompany();
        String templateId = templateId(authorOnboarding(browser, "Integrare colegi"));
        browser.post("/api/process-templates/" + templateId + "/retirement", null);
        int eventsBefore = eventsFor(templateId);

        ResponseEntity<String> refused = browser.post("/api/process-templates/" + templateId + "/retirement", null);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(eventsFor(templateId)).isEqualTo(eventsBefore);
    }

    @Test
    void retiringFreesTheTemplateName() throws Exception {
        buildTheCompany();
        String templateId = templateId(authorOnboarding(browser, "Integrare colegi"));
        browser.post("/api/process-templates/" + templateId + "/retirement", null);

        ResponseEntity<String> again = browser.post("/api/process-templates", onboarding("Integrare colegi"));

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void aRunStopsOnPurposeAndNoStepIsTouched() throws Exception {
        Company company = buildTheCompany();
        String templateId = templateId(authorOnboarding(browser, "Integrare colegi"));
        String instance = startARun(templateId, company.ionut());

        String conditionsBefore = stepConditionsOf(instance);

        ResponseEntity<String> stopped = browser.post(
                "/api/process-instances/" + instance + "/abandonment",
                "{\"reason\":\"Clientul a anulat comanda; nu mai continuăm.\"}");

        assertThat(stopped.getStatusCode())
                .as("V42's grant reached the owner; V2's could not have")
                .isEqualTo(HttpStatus.OK);
        JsonNode run = json.readTree(stopped.getBody());
        assertThat(run.get("state").asText()).isEqualTo("ABANDONED");
        assertThat(run.get("abandonedReason").asText()).isEqualTo("Clientul a anulat comanda; nu mai continuăm.");
        assertThat(run.get("abandonedAt").isNull()).isFalse();

        assertThat(jdbc.queryForObject(
                        "select completed_at from process_instance where id = ?::uuid", Object.class, instance))
                .as("abandoning is not completing")
                .isNull();
        assertThat(stepConditionsOf(instance))
                .as("not one step row moved; they describe tasks people are still holding")
                .isEqualTo(conditionsBefore);
    }

    @Test
    void nothingBecomesReachableInARunThatHasStopped() throws Exception {
        Company company = buildTheCompany();
        JsonNode template = authorOnboarding(browser, "Integrare colegi");
        String templateId = templateId(template);
        browser.post(
                "/api/process-templates/" + templateId + "/dependencies",
                edge(stepId(template, 1), stepId(template, 0)));
        String instance = startARun(templateId, company.ionut());

        browser.post("/api/process-instances/" + instance + "/abandonment", "{\"reason\":\"Nu mai are obiect.\"}");

        JsonNode run =
                json.readTree(browser.get("/api/process-instances/" + instance).getBody());
        assertThat(run.get("awaitingAssignment"))
                .as("nothing is waiting to be given out in a run nobody is running")
                .isEmpty();
        assertThat(run.get("steps").get(1).get("condition").asText())
                .as("the step behind the dependency never opens")
                .isEqualTo("PENDING");
    }

    @Test
    void stoppingARunWithNoReasonIsRefused() throws Exception {
        Company company = buildTheCompany();
        String instance = startARun(templateId(authorOnboarding(browser, "Integrare colegi")), company.ionut());

        ResponseEntity<String> refused =
                browser.post("/api/process-instances/" + instance + "/abandonment", "{\"reason\":\"   \"}");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbc.queryForObject("select state from process_instance where id = ?::uuid", String.class, instance))
                .isEqualTo("RUNNING");
    }

    @Test
    void aRunThatHasAlreadyStoppedCannotBeStoppedAgain() throws Exception {
        Company company = buildTheCompany();
        String instance = startARun(templateId(authorOnboarding(browser, "Integrare colegi")), company.ionut());
        browser.post("/api/process-instances/" + instance + "/abandonment", "{\"reason\":\"Prima oară.\"}");
        int eventsBefore = eventsOn(instance);

        ResponseEntity<String> refused =
                browser.post("/api/process-instances/" + instance + "/abandonment", "{\"reason\":\"A doua oară.\"}");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("INSTANCE_NOT_RUNNING");
        assertThat(eventsOn(instance)).isEqualTo(eventsBefore);
        assertThat(jdbc.queryForObject(
                        "select abandoned_reason from process_instance where id = ?::uuid", String.class, instance))
                .as("the first reason stands; the second attempt rewrites nothing")
                .isEqualTo("Prima oară.");
    }

    @Test
    void anEmployeeCannotStopARun() throws Exception {
        Company company = buildTheCompany();
        String instance = startARun(templateId(authorOnboarding(browser, "Integrare colegi")), company.ionut());

        assertThat(signedInBrowser("andrei@atelier.ro")
                        .post("/api/process-instances/" + instance + "/abandonment", "{\"reason\":\"Gata.\"}")
                        .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void theTasksLeftInFlightAreListedAsNeedingAttention() throws Exception {
        Company company = buildTheCompany();
        JsonNode template = authorOnboarding(browser, "Integrare colegi");
        String templateId = templateId(template);
        String instance = startARun(templateId, company.ionut());

        String step = firstStepOf(instance);

        RoundTripClient ionut = signedInBrowser("ionut@atelier.ro");
        ResponseEntity<String> assigned = ionut.post(
                "/api/process-instances/" + instance + "/steps/" + step + "/assignment",
                "{\"assigneeId\":\"%s\",\"deadline\":\"%s\"}".formatted(company.andrei(), tomorrow()));
        assertThat(assigned.getStatusCode())
                .as(String.valueOf(assigned.getBody()))
                .isEqualTo(HttpStatus.OK);

        JsonNode running =
                json.readTree(browser.get("/api/process-instances/" + instance).getBody());
        assertThat(running.get("needingAttention"))
                .as("a live run has open tasks; that is work, not a problem")
                .isEmpty();

        browser.post("/api/process-instances/" + instance + "/abandonment", "{\"reason\":\"Colegul nu mai vine.\"}");

        JsonNode stopped =
                json.readTree(browser.get("/api/process-instances/" + instance).getBody());
        assertThat(idsIn(stopped.get("needingAttention")))
                .as("Andrei is still holding this one, and PROCESS did not close it")
                .contains(step);
    }

    private String firstStepOf(String instance) throws Exception {
        return json.readTree(browser.get("/api/process-instances/" + instance).getBody())
                .get("steps")
                .get(0)
                .get("id")
                .asText();
    }

    private static String tomorrow() {
        return java.time.Instant.now().plusSeconds(86_400).toString();
    }

    private String startARun(String templateId, UUID processOwner) throws Exception {
        ResponseEntity<String> started = browser.post(
                "/api/process-instances",
                "{\"templateId\":\"%s\",\"name\":\"Integrare Andrei\",\"processOwnerId\":\"%s\"}"
                        .formatted(templateId, processOwner));
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(started.getBody()).get("id").asText();
    }

    private String stepConditionsOf(String instance) {
        return String.valueOf(jdbc.queryForList(
                "select id::text, condition from instance_step where instance_id = ?::uuid order by id", instance));
    }

    private static java.util.List<String> namesIn(JsonNode templates) {
        java.util.List<String> names = new java.util.ArrayList<>();
        templates.forEach(each -> names.add(each.get("name").asText()));
        return names;
    }

    private static java.util.List<String> idsIn(JsonNode ids) {
        java.util.List<String> found = new java.util.ArrayList<>();
        ids.forEach(each -> found.add(each.asText()));
        return found;
    }
}
