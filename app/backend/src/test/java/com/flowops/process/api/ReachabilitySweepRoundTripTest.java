package com.flowops.process.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.process.application.sweep.ReachabilitySweep;
import com.flowops.process.infrastructure.task.TaskMovementListener;
import com.flowops.support.RoundTripClient;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Tag("PROCESS-ASSIGN-REACHABLE-01")
class ReachabilitySweepRoundTripTest extends ProcessScenarioTest {
    @MockitoBean
    private TaskMovementListener listenerIsSilencedHere;

    @Autowired
    private ReachabilitySweep sweep;

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

    private String assign(int position) throws Exception {
        return json.readTree(ioana.post(
                                "/api/process-instances/" + instanceId() + "/steps/" + step(position) + "/assignment",
                                "{\"assigneeId\":\"%s\",\"deadline\":\"%s\"}"
                                        .formatted(
                                                company.elena(), Instant.now().plusSeconds(86_400)))
                        .getBody())
                .get("steps")
                .get(position)
                .get("taskId")
                .asText();
    }

    private String assignAndClose(int position) throws Exception {
        String task = assign(position);

        RoundTripClient elena = signedInBrowser("elena@atelier.ro");
        elena.post("/api/tasks/" + task + "/accept", "");
        elena.post("/api/tasks/" + task + "/start", "");
        elena.post("/api/tasks/" + task + "/complete", "{\"note\":\"gata\",\"externalLink\":null}");
        browser.post("/api/tasks/" + task + "/approve", "{\"score\":4,\"comment\":\"bine\"}");
        assertThat(browser.post("/api/tasks/" + task + "/close", "").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        return task;
    }

    @Test
    void withTheAnnouncementSilencedAClosedTaskLeavesTheGraphWhereItWas() throws Exception {
        assignAndClose(0);

        JsonNode after = reread();
        assertThat(after.get("steps").get(0).get("condition").asText()).isEqualTo("ASSIGNED");
        assertThat(after.get("steps").get(1).get("condition").asText()).isEqualTo("PENDING");
    }

    @Test
    void theSweepAloneAdvancesAGraphNothingAnnounced() throws Exception {
        assignAndClose(0);

        sweep.reconcile();

        JsonNode after = reread();
        assertThat(after.get("steps").get(0).get("condition").asText()).isEqualTo("CLOSED");
        assertThat(after.get("steps").get(1).get("condition").asText()).isEqualTo("REACHABLE");
    }

    @Test
    void runningTheSweepTwiceAdvancesOnceAndAnnouncesOnce() throws Exception {
        assignAndClose(0);

        sweep.reconcile();
        int afterFirst = eventsOn(instanceId());
        sweep.reconcile();

        assertThat(eventsOn(instanceId())).isEqualTo(afterFirst);
        assertThat(reread().get("steps").get(1).get("condition").asText()).isEqualTo("REACHABLE");
    }

    @Test
    void theSweepAdvancesPastAnOpenDatedTaskOnAThreadWithNoCaller() throws Exception {
        assign(2);

        assignAndClose(0);

        AtomicReference<Throwable> escaped = new AtomicReference<>();
        Thread scheduler = new Thread(sweep::reconcile, "no-caller-here");
        scheduler.setUncaughtExceptionHandler((thread, failure) -> escaped.set(failure));
        scheduler.start();
        scheduler.join();

        assertThat(escaped.get()).isNull();
        JsonNode after = reread();
        assertThat(after.get("steps").get(0).get("condition").asText()).isEqualTo("CLOSED");
        assertThat(after.get("steps").get(1).get("condition").asText()).isEqualTo("REACHABLE");
    }

    @Test
    void aSweepWithNothingToDoChangesNothing() throws Exception {
        int before = eventsOn(instanceId());

        sweep.reconcile();

        assertThat(eventsOn(instanceId())).isEqualTo(before);
        assertThat(reread().get("steps").get(0).get("condition").asText()).isEqualTo("REACHABLE");
    }
}
