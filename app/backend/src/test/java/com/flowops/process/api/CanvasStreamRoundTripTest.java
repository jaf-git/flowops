package com.flowops.process.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.canvas.application.stream.CanvasHeartbeat;
import com.flowops.support.RoundTripClient;
import com.flowops.support.SseConversation;
import com.flowops.support.SseConversation.SseEvent;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("CANVAS-VIEW-PROCESS-01")
class CanvasStreamRoundTripTest extends ProcessScenarioTest {
    private static final Duration SOON = Duration.ofSeconds(10);

    @Autowired
    private CanvasHeartbeat heartbeat;

    private Company company;
    private RoundTripClient andrei;

    private String run;

    @BeforeEach
    void buildTheCompanyFirst() throws Exception {
        company = buildTheCompany();
        andrei = signedInBrowser("andrei@atelier.ro");
    }

    private String taskAndreiIsWorkingOn() throws Exception {
        JsonNode template = authorOnboarding(browser, "Integrare");

        ResponseEntity<String> started = browser.post(
                "/api/process-instances",
                "{\"templateId\":\"%s\",\"name\":\"Integrare — Elena\",\"processOwnerId\":\"%s\"}"
                        .formatted(templateId(template), company.maria()));
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode instance = json.readTree(started.getBody());
        run = instance.get("id").asText();
        String first = instance.get("steps").get(0).get("id").asText();

        ResponseEntity<String> assigned = browser.post(
                "/api/process-instances/%s/steps/%s/assignment".formatted(run, first),
                "{\"assigneeId\":\"%s\",\"deadline\":\"2026-09-30T15:00:00Z\"}".formatted(company.andrei()));
        assertThat(assigned.getStatusCode()).isEqualTo(HttpStatus.OK);

        String task = json.readTree(assigned.getBody())
                .get("steps")
                .get(0)
                .get("taskId")
                .asText();

        assertThat(andrei.post("/api/tasks/" + task + "/accept", "").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(andrei.post("/api/tasks/" + task + "/start", "").getStatusCode())
                .isEqualTo(HttpStatus.OK);

        return task;
    }

    private long cursorNow() throws Exception {
        ResponseEntity<String> answer = browser.get("/api/canvas/cursor");
        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json.readTree(answer.getBody()).get("cursor").asLong();
    }

    private SseConversation mariaWatching(Long resumeFrom) throws Exception {
        return browser.openEventStream("/api/canvas/stream/process-instances/" + run, resumeFrom)
                .established(SOON);
    }

    private void block(String task, String reason) {
        assertThat(andrei.post("/api/tasks/" + task + "/block", "{\"reason\":\"%s\"}".formatted(reason))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void aBlockByOnePersonReachesAnotherPersonsOpenBoard() throws Exception {
        String task = taskAndreiIsWorkingOn();
        long before = cursorNow();

        try (SseConversation maria = mariaWatching(before)) {
            block(task, "Așteptăm contractul semnat");

            SseEvent delta = maria.awaiting("delta", SOON);

            assertThat(delta.data()).contains(task);
            assertThat(delta.data()).contains("TASK_BLOCKED");

            assertThat(Long.parseLong(delta.id())).isGreaterThan(before);

            assertThat(andrei.post("/api/tasks/" + task + "/unblock", "").getStatusCode())
                    .isEqualTo(HttpStatus.OK);

            SseEvent second = maria.awaiting("delta", SOON);

            assertThat(second.data()).contains(task);
            assertThat(Long.parseLong(second.id())).isGreaterThan(Long.parseLong(delta.id()));
        }
    }

    @Test
    void aCutCursorReplaysWhatTheBoardMissed() throws Exception {
        String task = taskAndreiIsWorkingOn();
        long before = cursorNow();

        block(task, "Așteptăm contractul semnat");
        assertThat(andrei.post("/api/tasks/" + task + "/unblock", "").getStatusCode())
                .isEqualTo(HttpStatus.OK);

        try (SseConversation maria = mariaWatching(before)) {
            SseEvent replayed = maria.awaiting("delta", SOON);

            assertThat(replayed.data()).contains(task);
            assertThat(Long.parseLong(replayed.id())).isGreaterThan(before);
        }
    }

    @Test
    void aGapTooOldToReplayIsRefusedOnTheStreamRatherThanPretendedPast() throws Exception {
        taskAndreiIsWorkingOn();

        try (SseConversation maria = mariaWatching(-100_000L)) {
            SseEvent refusal = maria.awaiting("stale", SOON);

            assertThat(refusal.data()).contains("CURSOR_TOO_OLD");
        }
    }

    @Test
    void openingAndWatchingARunWritesNothingAtAll() throws Exception {
        String task = taskAndreiIsWorkingOn();
        int eventsBeforeWatching = eventsOn(run);

        try (SseConversation maria = mariaWatching(cursorNow())) {
            block(task, "Așteptăm contractul semnat");
            maria.awaiting("delta", SOON);
        }

        assertThat(eventsOn(run)).isEqualTo(eventsBeforeWatching);
    }

    @Test
    void aStreamIsRefusedToSomebodyWhoMayNotSeeTheRun() throws Exception {
        taskAndreiIsWorkingOn();

        RoundTripClient elena = signedInBrowser("elena@atelier.ro");

        try (SseConversation refused = elena.openEventStream(
                        "/api/canvas/stream/process-instances/" + UUID.randomUUID(), null)
                .established(SOON)) {
            assertThat(refused.status()).isEqualTo(HttpStatus.NOT_FOUND.value());
        }
    }

    @Test
    void anExistingRunOutOfScopeIsIndistinguishableFromOneThatDoesNotExist() throws Exception {
        taskAndreiIsWorkingOn();

        RoundTripClient elena = signedInBrowser("elena@atelier.ro");

        try (SseConversation absent = elena.openEventStream(
                                "/api/canvas/stream/process-instances/" + UUID.randomUUID(), null)
                        .established(SOON);
                SseConversation outOfScope = elena.openEventStream("/api/canvas/stream/process-instances/" + run, null)
                        .established(SOON)) {
            assertThat(outOfScope.status())
                    .as("a run Elena may not see must not answer differently from one that is not there")
                    .isEqualTo(absent.status());
            assertThat(outOfScope.status()).isEqualTo(HttpStatus.NOT_FOUND.value());
        }
    }

    @Test
    void aFirstConnectionResumesFromTheCursorQueryParameter() throws Exception {
        String task = taskAndreiIsWorkingOn();
        long before = cursorNow();

        block(task, "Lipsește avizul");

        try (SseConversation maria = browser.openEventStream(
                        "/api/canvas/stream/process-instances/" + run + "?cursor=" + before, null)
                .established(SOON)) {
            SseEvent replayed = maria.awaiting("delta", SOON);

            assertThat(replayed.data()).contains(task);
            assertThat(replayed.data()).contains("TASK_BLOCKED");
            assertThat(Long.parseLong(replayed.id())).isGreaterThan(before);
        }
    }

    @Test
    void aBeatReachesTheClientUnderItsOwnNameAndCarriesNoCursor() throws Exception {
        taskAndreiIsWorkingOn();

        try (SseConversation maria = mariaWatching(null)) {
            heartbeat.beat();

            SseEvent beat = maria.awaiting("beat", SOON);

            assertThat(beat.id())
                    .as("a beat that moved the cursor would let a reconnect skip what it should replay")
                    .isNull();
        }
    }
}
