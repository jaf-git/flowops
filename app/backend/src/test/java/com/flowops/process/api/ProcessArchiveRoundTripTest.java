package com.flowops.process.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.RoundTripClient;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("PROCESS-ARCHIVE-INSTANCE-01")
class ProcessArchiveRoundTripTest extends ProcessScenarioTest {
    private Company company;
    private JsonNode instance;

    @BeforeEach
    void aRunToPutAway() throws Exception {
        company = buildTheCompany();
        instance = startARunOwnedBy(company.ioana());
    }

    private JsonNode startARunOwnedBy(UUID processOwner) throws Exception {
        JsonNode template = authorOnboarding(browser, "Integrare " + UUID.randomUUID());
        return json.readTree(browser.post(
                        "/api/process-instances",
                        "{\"templateId\":\"%s\",\"name\":\"Integrare — %s\",\"processOwnerId\":\"%s\"}"
                                .formatted(templateId(template), UUID.randomUUID(), processOwner))
                .getBody());
    }

    private String instanceId() {
        return instance.get("id").asText();
    }

    private boolean onTheBoard(String runId) throws Exception {
        return listed(browser, "ON_THE_BOARD", runId);
    }

    private boolean listed(RoundTripClient caller, String population, String runId) throws Exception {
        JsonNode list = json.readTree(
                caller.get("/api/process-instances?population=" + population).getBody());
        for (JsonNode run : list.get("instances")) {
            if (run.get("id").asText().equals(runId)) {
                return true;
            }
        }
        return false;
    }

    private void abandon(String runId) {
        browser.post(
                "/api/process-instances/" + runId + "/abandonment", "{\"reason\":\"clientul a amânat proiectul\"}");
    }

    @Test
    void aFinishedRunLeavesTheBoardAndComesBack() throws Exception {
        abandon(instanceId());
        assertThat(onTheBoard(instanceId()))
                .as("an abandoned run is still on the board until somebody puts it away")
                .isTrue();

        ResponseEntity<String> archived = browser.post("/api/process-instances/" + instanceId() + "/archive", null);
        assertThat(archived.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(onTheBoard(instanceId()))
                .as("archiving takes the run off the board")
                .isFalse();

        ResponseEntity<String> restored = browser.delete("/api/process-instances/" + instanceId() + "/archive");
        assertThat(restored.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(onTheBoard(instanceId())).as("restoring puts it back").isTrue();
    }

    @Test
    void aRunStillGoingIsRefusedWithASentence() throws Exception {
        ResponseEntity<String> refused = browser.post("/api/process-instances/" + instanceId() + "/archive", null);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("INSTANCE_STILL_RUNNING");
        assertThat(onTheBoard(instanceId()))
                .as("a refused archive changes nothing")
                .isTrue();
    }

    @Test
    void anEmployeeMayNotPutARunAway() throws Exception {
        abandon(instanceId());
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");

        ResponseEntity<String> refused = andrei.post("/api/process-instances/" + instanceId() + "/archive", null);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(onTheBoard(instanceId()))
                .as("a refused caller changes nothing")
                .isTrue();
    }

    @Test
    void aManagerMayNotPutAwayARunOutsideTheirScope() throws Exception {
        JsonNode elenas = startARunOwnedBy(company.elena());
        String elenasRun = elenas.get("id").asText();
        abandon(elenasRun);
        RoundTripClient ioana = signedInBrowser("ioana@atelier.ro");

        ResponseEntity<String> refused = ioana.post("/api/process-instances/" + elenasRun + "/archive", null);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(onTheBoard(elenasRun))
                .as("a run outside a caller's scope is untouched by them")
                .isTrue();
    }

    @Test
    void archivingTakesNothingAwayFromTheHistory() throws Exception {
        abandon(instanceId());
        int eventsBefore = eventsOn(instanceId());

        browser.post("/api/process-instances/" + instanceId() + "/archive", null);

        assertThat(eventsOn(instanceId()))
                .as("archiving records itself and takes nothing away")
                .isEqualTo(eventsBefore + 1);

        assertThat(browser.get("/api/process-instances/" + instanceId()).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void thePopulationTheFiguresAreComputedOverDoesNotChangeWhenARunIsPutAway() throws Exception {
        abandon(instanceId());
        browser.post("/api/process-instances/" + instanceId() + "/archive", null);

        assertThat(listed(browser, "ON_THE_BOARD", instanceId()))
                .as("the board is what archiving is a statement about")
                .isFalse();
        assertThat(listed(browser, "EVERY_RUN", instanceId()))
                .as("every figure counts an archived run exactly as it counted it before")
                .isTrue();
    }

    @Test
    void aPopulationThatDoesNotExistIsRefusedRatherThanBreaking() {
        ResponseEntity<String> refused = browser.get("/api/process-instances?population=EVERYTHING");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void askingForEveryRunDoesNotWidenWhoseRunsAreAnswered() throws Exception {
        JsonNode elenas = startARunOwnedBy(company.elena());
        String elenasRun = elenas.get("id").asText();
        RoundTripClient ioana = signedInBrowser("ioana@atelier.ro");

        assertThat(listed(ioana, "EVERY_RUN", elenasRun))
                .as("a wider population is not a wider scope")
                .isFalse();
        assertThat(listed(browser, "EVERY_RUN", elenasRun))
                .as("and the owner still sees it, so the absence above is scope and not an empty list")
                .isTrue();
    }
}
