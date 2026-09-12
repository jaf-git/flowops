package com.flowops.process.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.CompanyScenarioTest;
import com.flowops.support.RoundTripClient;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("PROCESS-START-FROM-DESCRIPTIONS-01")
class ProcessFromDescriptionsRoundTripTest extends CompanyScenarioTest {
    private static final String FROM_DESCRIPTIONS = "/api/process-instances/from-descriptions";

    @Test
    void makesTheTasksAndTheRunInOneRequest() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);

        ResponseEntity<String> started = maria.post(
                FROM_DESCRIPTIONS,
                """
                {"name":"Aurora Coffee — brand strategy","processOwnerId":"%s","steps":[
                  {"title":"Discovery workshop","description":"What they believe they sell","assigneeId":"%s",
                   "deadline":null,"priority":"NORMAL"},
                  {"title":"Competitor scan","description":null,"assigneeId":"%s","deadline":null,"priority":"HIGH"},
                  {"title":"Positioning statement","description":null,"assigneeId":"%s","deadline":null,
                   "priority":"NORMAL"}
                ]}"""
                        .formatted(company.ionut(), company.andrei(), company.andrei(), company.elena()));

        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode run = json.readTree(started.getBody());

        assertThat(run.get("name").asText()).isEqualTo("Aurora Coffee — brand strategy");
        assertThat(run.get("steps"))
                .as("one step per description, in the order they were given")
                .hasSize(3);
        assertThat(run.get("steps").findValues("title").stream()
                        .map(JsonNode::asText)
                        .toList())
                .containsExactly("Discovery workshop", "Competitor scan", "Positioning statement");

        for (JsonNode step : run.get("steps")) {
            assertThat(step.get("taskId").isNull())
                    .as("described work becomes work, not a placeholder")
                    .isFalse();
        }
    }

    @Test
    void leavesNothingBehindWhenOneStepIsRefused() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient ioana = signedInBrowser("ioana@atelier.ro");

        long tasksBefore = countOf("task");
        long runsBefore = countOf("process_instance");

        ResponseEntity<String> refused = ioana.post(
                FROM_DESCRIPTIONS,
                """
                {"name":"A plan she cannot wholly direct","processOwnerId":"%s","steps":[
                  {"title":"Something for her own report","description":null,"assigneeId":"%s","deadline":null,
                   "priority":"NORMAL"},
                  {"title":"Something for somebody else's","description":null,"assigneeId":"%s","deadline":null,
                   "priority":"NORMAL"}
                ]}"""
                        .formatted(company.ioana(), company.andrei(), company.elena()));

        assertThat(refused.getStatusCode()).isNotEqualTo(HttpStatus.CREATED);
        assertThat(countOf("task"))
                .as("the first step's task must not outlive the second step's refusal")
                .isEqualTo(tasksBefore);
        assertThat(countOf("process_instance")).isEqualTo(runsBefore);
    }

    @Test
    void refusesARunWithNoWorkInIt() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);

        ResponseEntity<String> refused = maria.post(
                FROM_DESCRIPTIONS,
                """
                {"name":"Nothing at all","processOwnerId":"%s","steps":[]}"""
                        .formatted(company.ionut()));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void refusesAProcessOwnerWhoCannotSteerIt() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient maria = signedInBrowser(OWNER_EMAIL);

        ResponseEntity<String> refused = maria.post(
                FROM_DESCRIPTIONS,
                """
                {"name":"Steered by nobody","processOwnerId":"%s","steps":[
                  {"title":"Anything","description":null,"assigneeId":"%s","deadline":null,"priority":"NORMAL"}
                ]}"""
                        .formatted(UUID.randomUUID(), company.andrei()));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(countOf("process_instance")).isZero();
    }

    @Test
    void refusesACallerWhoMayNotStartRuns() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");

        ResponseEntity<String> refused = andrei.post(
                FROM_DESCRIPTIONS,
                """
                {"name":"Not his to start","processOwnerId":"%s","steps":[
                  {"title":"Anything","description":null,"assigneeId":"%s","deadline":null,"priority":"NORMAL"}
                ]}"""
                        .formatted(company.ionut(), company.andrei()));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private long countOf(String table) {
        Long rows = jdbc.queryForObject("select count(*) from " + table, Long.class);
        return rows == null ? 0 : rows;
    }
}
