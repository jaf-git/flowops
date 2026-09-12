package com.flowops.nodepipeline.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.CompanyScenarioTest;
import com.flowops.support.RoundTripClient;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

@Tag("NODEPIPE-ELAPSED-01")
@Tag("roundtrip")
class JobClockAndProcessRoundTripTest extends CompanyScenarioTest {
    private UUID job;

    @BeforeEach
    void anEngagementThatSpentTimeWaiting() throws Exception {
        buildTheCompany();

        UUID maria = jdbc.queryForObject("select id from auth_user where email = ?", UUID.class, OWNER_EMAIL);
        job = UUID.randomUUID();

        Instant opened = Instant.now().minus(19, ChronoUnit.DAYS);
        Instant closed = Instant.now();

        jdbc.update(
                """
                insert into job (id, name, status, standing, opened_at, closed_at, opened_by,
                                 last_activity_at, shape_eligible, is_rework)
                values (?, 'Meridian launch', 'CLOSED', false, ?, ?, ?, ?, true, false)
                """,
                job,
                Timestamp.from(opened),
                Timestamp.from(closed),
                maria,
                Timestamp.from(closed));
    }

    @Test
    void anEngagementWithNoWaitsIsAllWorkingTimeAndIsNotA404() throws Exception {
        JsonNode elapsed = json.readTree(
                browser.get("/api/node-pipeline/jobs/" + job + "/elapsed").getBody());

        assertThat(elapsed.get("externalWaitDays").asInt())
                .as("nobody was waiting on a client or a supplier")
                .isZero();
        assertThat(elapsed.get("workingDays").asInt())
                .as("so every day of it is ours, and the two must add up")
                .isEqualTo(elapsed.get("totalDays").asInt());
    }

    @Test
    void anEngagementNobodyHasAnswersNotFound() throws Exception {
        assertThat(browser.get("/api/node-pipeline/jobs/00000000-0000-0000-0000-000000000000/elapsed")
                        .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void theProcessPickerAnswersAndAnEmptyLibraryIsOrdinary() throws Exception {
        JsonNode startable = json.readTree(
                browser.get("/api/node-pipeline/processes/startable").getBody());

        assertThat(startable.isArray())
                .as("a workspace with nothing written down still gets a list, not a failure")
                .isTrue();
    }

    @Test
    void aProcessThatDoesNotExistIsRefusedInProcessesOwnWords() throws Exception {
        var refused = browser.post(
                "/api/node-pipeline/processes/00000000-0000-0000-0000-000000000000/runs",
                "{\"processOwnerId\":\"" + UUID.randomUUID() + "\"}");

        assertThat(refused.getStatusCode())
                .as("a missing template is not found; it is never a server fault")
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(refused.getStatusCode().is5xxServerError()).isFalse();
    }

    @Test
    void neitherRouteAnswersWithoutASession() throws Exception {
        RoundTripClient anonymous = new RoundTripClient(rest);

        assertThat(anonymous.get("/api/node-pipeline/jobs/" + job + "/elapsed").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(anonymous.get("/api/node-pipeline/processes/startable").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
