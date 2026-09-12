package com.flowops.process.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.RoundTripClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("PROCESS-VIEW-INSTANCE-01")
class InstanceReadsItsTasksRoundTripTest extends ProcessScenarioTest {
    private Company company;
    private JsonNode instance;

    @BeforeEach
    void aRunWithOneStepAssigned() throws Exception {
        company = buildTheCompany();
        JsonNode template = authorOnboarding(browser, "Integrare — citire vie");
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

    private String assignFirstStepToElena() throws Exception {
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

    private JsonNode readStep(RoundTripClient who, int position) throws Exception {
        ResponseEntity<String> read = who.get("/api/process-instances/" + instanceId());
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json.readTree(read.getBody()).get("steps").get(position);
    }

    @Test
    void editingATasksDescriptionChangesWhatTheRunShows() throws Exception {
        String taskId = assignFirstStepToElena();

        ResponseEntity<String> edited = browser.exchange(
                HttpMethod.PUT,
                "/api/tasks/" + taskId,
                "{\"deadline\":\"%s\",\"priority\":\"HIGH\",\"description\":\"laptop, acces VPN, badge\"}"
                        .formatted(java.time.Instant.now().plusSeconds(172_800)));
        assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(readStep(browser, 0).get("description").asText()).isEqualTo("laptop, acces VPN, badge");
    }

    @Test
    void aStepWithNoTaskStillShowsItsPlannedTitleAndSaysItIsPlanned() throws Exception {
        JsonNode third = readStep(browser, 2);

        assertThat(third.get("taskId").isNull()).isTrue();
        assertThat(third.get("title").asText()).isEqualTo("Evaluare la o lună");
        assertThat(third.get("planned").asBoolean()).isTrue();
    }

    @Test
    void aStepWithATaskIsNotPlannedAndCarriesTheTasksOwnTitle() throws Exception {
        assignFirstStepToElena();

        JsonNode first = readStep(browser, 0);

        assertThat(first.get("planned").asBoolean()).isFalse();
        assertThat(first.get("title").asText()).isEqualTo("Pregătește echipamentul");
    }

    @Test
    void theRunShowsTheDeadlineTheTaskHasNow() throws Exception {
        String taskId = assignFirstStepToElena();
        String moved = java.time.Instant.now().plusSeconds(432_000).toString();

        browser.exchange(
                HttpMethod.PUT,
                "/api/tasks/" + taskId,
                "{\"deadline\":\"%s\",\"priority\":\"NORMAL\",\"description\":\"laptop, acces\"}".formatted(moved));

        assertThat(readStep(browser, 0).get("deadline").asText()).startsWith(moved.substring(0, 16));
    }
}
