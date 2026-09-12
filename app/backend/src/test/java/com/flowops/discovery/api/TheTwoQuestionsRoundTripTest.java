package com.flowops.discovery.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.CompanyScenarioTest;
import com.flowops.support.RoundTripClient;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("DISCOVERY-RECORD-OUTPUT-01")
@Tag("DISCOVERY-BLOCK-NODE-01")
@Tag("DISCOVERY-NUDGE-COMPLETION-01")
class TheTwoQuestionsRoundTripTest extends CompanyScenarioTest {
    private String conversationBetween(RoundTripClient who, UUID person) throws Exception {
        ResponseEntity<String> started = who.post("/api/conversations", "{\"personId\":\"%s\"}".formatted(person));
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(started.getBody()).get("id").asText();
    }

    private String said(RoundTripClient who, String conversation, String body) throws Exception {
        ResponseEntity<String> sent =
                who.post("/api/conversations/" + conversation + "/messages", "{\"body\":\"%s\"}".formatted(body));
        assertThat(sent.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(sent.getBody()).get("id").asText();
    }

    private JsonNode openJob(RoundTripClient who, String messageId, String name) throws Exception {
        ResponseEntity<String> opened =
                who.post("/api/discovery/jobs", "{\"messageId\":\"%s\",\"name\":\"%s\"}".formatted(messageId, name));
        assertThat(opened.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(opened.getBody());
    }

    private ResponseEntity<String> mark(
            RoundTripClient who, String messageId, String jobId, String direction, UUID performer) {
        String body = performer == null
                ? "{\"messageId\":\"%s\",\"jobId\":\"%s\",\"direction\":\"%s\",\"performerId\":null}"
                        .formatted(messageId, jobId, direction)
                : "{\"messageId\":\"%s\",\"jobId\":\"%s\",\"direction\":\"%s\",\"performerId\":\"%s\"}"
                        .formatted(messageId, jobId, direction, performer);
        return who.post("/api/discovery/nodes", body);
    }

    private ResponseEntity<String> output(RoundTripClient who, String node, String outputType) {
        return who.post("/api/discovery/nodes/" + node + "/output", "{\"outputType\":\"%s\"}".formatted(outputType));
    }

    private ResponseEntity<String> block(RoundTripClient who, String node, String waitingOn) {
        return who.post("/api/discovery/nodes/" + node + "/block", "{\"waitingOn\":\"%s\"}".formatted(waitingOn));
    }

    private ResponseEntity<String> resume(RoundTripClient who, String node) {
        return who.post("/api/discovery/nodes/" + node + "/resume", "");
    }

    private ResponseEntity<String> answerNudge(RoundTripClient who, String node, String answer) {
        return who.post("/api/discovery/nodes/" + node + "/nudge-answer", "{\"answer\":\"%s\"}".formatted(answer));
    }

    private String stateOf(String node) {
        return jdbc.queryForObject("select state from work_node where id = ?::uuid", String.class, node);
    }

    private String outputTypeOf(String node) {
        return jdbc.queryForObject("select output_type from work_node where id = ?::uuid", String.class, node);
    }

    private long phaseRowsOn(String node) {
        return jdbc.queryForObject(
                "select count(*) from node_phase_row where work_node_id = ?::uuid", Long.class, node);
    }

    private record Scene(String job, String solo, String requested) {}

    private Scene aRebrandingUnderway(Company company) throws Exception {
        String conversation = conversationBetween(browser, company.andrei());
        String brief = said(browser, conversation, "Aurora Coffee vrea un rebranding complet");
        JsonNode opened = openJob(browser, brief, "Rebranding Aurora Coffee");

        String asked = said(browser, conversation, "Andrei, poti sa scrii postarile pentru Aurora?");
        ResponseEntity<String> marked = mark(browser, asked, opened.get("jobId").asText(), "REQUEST", company.andrei());
        assertThat(marked.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        return new Scene(
                opened.get("jobId").asText(),
                opened.get("nodeId").asText(),
                json.readTree(marked.getBody()).get("nodeId").asText());
    }

    @Test
    void doneWithAnOutputCompletesTheWorkAndWritesWhatItProduced() throws Exception {
        Company company = buildTheCompany();
        Scene scene = aRebrandingUnderway(company);
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");

        ResponseEntity<String> done = output(andrei, scene.requested(), "TEXT");

        assertThat(done.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = json.readTree(done.getBody());
        assertThat(body.get("state").asText()).isEqualTo("COMPLETED");
        assertThat(body.get("outputType").asText()).isEqualTo("TEXT");

        assertThat(stateOf(scene.requested()))
                .as("Assigned to InProgress and InProgress to Completed, both drawn by machine 4.1")
                .isEqualTo("COMPLETED");
        assertThat(outputTypeOf(scene.requested()))
                .as("the strongest component of the fingerprint, and it is never inferred")
                .isEqualTo("TEXT");
        assertThat(jdbc.queryForObject(
                        "select started_at is not null from work_node where id = ?::uuid",
                        Boolean.class,
                        scene.requested()))
                .as("a closed_at minus a missing started_at is not a duration, it is a guess")
                .isTrue();

        assertThat(jdbc.queryForObject(
                        "select count(*) from node_phase_row where work_node_id = ?::uuid"
                                + " and phase = 'WORK' and ended_at is not null",
                        Long.class,
                        scene.requested()))
                .as("the work segment was opened and sealed")
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                        "select count(*) from node_phase_row where work_node_id = ?::uuid"
                                + " and phase = 'REVIEW' and ended_at is null",
                        Long.class,
                        scene.requested()))
                .as("machine 4.1's clock table maps Completed to REVIEW")
                .isEqualTo(1L);
    }

    @Test
    void noOutputYetRecordsTheAnswerAndLeavesTheWorkRunning() throws Exception {
        Company company = buildTheCompany();
        Scene scene = aRebrandingUnderway(company);
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");

        ResponseEntity<String> nothingYet = output(andrei, scene.requested(), "NONE");

        assertThat(nothingYet.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(nothingYet.getBody()).get("state").asText()).isEqualTo("IN_PROGRESS");
        assertThat(stateOf(scene.requested()))
                .as("'no output yet' keeps the node open rather than closing it emptily")
                .isEqualTo("IN_PROGRESS");
        assertThat(outputTypeOf(scene.requested()))
                .as("the answer is recorded even though the work has not ended")
                .isEqualTo("NONE");
        assertThat(jdbc.queryForObject(
                        "select closed_at from work_node where id = ?::uuid", String.class, scene.requested()))
                .isNull();

        ResponseEntity<String> properly = output(andrei, scene.requested(), "TEXT");

        assertThat(properly.getStatusCode())
                .as("a NONE node must still be able to say what it produced, or it is stuck for all time")
                .isEqualTo(HttpStatus.OK);
        assertThat(stateOf(scene.requested())).isEqualTo("COMPLETED");
        assertThat(outputTypeOf(scene.requested())).isEqualTo("TEXT");
    }

    @Test
    void aSecondOutputAnswerIsRefusedAndTheFirstStands() throws Exception {
        Company company = buildTheCompany();
        Scene scene = aRebrandingUnderway(company);
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        assertThat(output(andrei, scene.requested(), "TEXT").getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> again = output(andrei, scene.requested(), "DESIGN");

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(again.getBody()).get("code").asText()).isEqualTo("OUTPUT_ALREADY_RECORDED");
        assertThat(outputTypeOf(scene.requested()))
                .as("the first observation is not overwritten by the second")
                .isEqualTo("TEXT");
    }

    @Test
    void anUnknownUnitOfWorkIsRefusedRatherThanInvented() throws Exception {
        buildTheCompany();

        ResponseEntity<String> refused = output(browser, UUID.randomUUID().toString(), "TEXT");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        JsonNode error = json.readTree(refused.getBody());
        assertThat(error.get("code").asText()).isEqualTo("UNKNOWN_WORK_NODE");
        assertThat(error.get("details").get(0).get("field").asText()).isEqualTo("nodeId");
        assertThat(jdbc.queryForObject("select count(*) from work_node", Long.class))
                .isZero();
    }

    @Test
    void blockingOnAClientAndOnAColleagueOpenTwoDifferentKindsOfWait() throws Exception {
        Company company = buildTheCompany();
        Scene scene = aRebrandingUnderway(company);
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");

        ResponseEntity<String> onTheClient = block(andrei, scene.requested(), "CLIENT");
        ResponseEntity<String> onAColleague = block(browser, scene.solo(), "COLLEAGUE");

        assertThat(onTheClient.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(onTheClient.getBody()).get("openPhase").asText())
                .isEqualTo("EXTERNAL_WAIT");
        assertThat(onAColleague.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(onAColleague.getBody()).get("openPhase").asText())
                .isEqualTo("INTERNAL_WAIT");

        assertThat(stateOf(scene.requested())).isEqualTo("BLOCKED");
        assertThat(stateOf(scene.solo())).isEqualTo("BLOCKED");

        assertThat(jdbc.queryForObject(
                        "select phase from node_phase_row where work_node_id = ?::uuid and ended_at is null",
                        String.class,
                        scene.requested()))
                .as("a client's silence is external and never enters a performance figure — I4")
                .isEqualTo("EXTERNAL_WAIT");
        assertThat(jdbc.queryForObject(
                        "select waiting_on from node_phase_row where work_node_id = ?::uuid and ended_at is null",
                        String.class,
                        scene.requested()))
                .isEqualTo("CLIENT");
        assertThat(jdbc.queryForObject(
                        "select phase from node_phase_row where work_node_id = ?::uuid and ended_at is null",
                        String.class,
                        scene.solo()))
                .as("a colleague is inside the business, and the same stretch of time means something else")
                .isEqualTo("INTERNAL_WAIT");
        assertThat(jdbc.queryForObject(
                        "select waiting_on from node_phase_row where work_node_id = ?::uuid and ended_at is null",
                        String.class,
                        scene.solo()))
                .isEqualTo("COLLEAGUE");
    }

    @Test
    void resumeSealsTheWaitOpensFreshWorkAndNothingAnywhereHoldsATotal() throws Exception {
        Company company = buildTheCompany();
        Scene scene = aRebrandingUnderway(company);
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        assertThat(block(andrei, scene.requested(), "CLIENT").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(phaseRowsOn(scene.requested()))
                .as("one work segment, sealed, and one wait — separate rows from the moment they exist")
                .isEqualTo(2L);

        ResponseEntity<String> back = resume(andrei, scene.requested());

        assertThat(back.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(back.getBody()).get("openPhase").asText()).isEqualTo("WORK");
        assertThat(json.readTree(back.getBody()).hasNonNull("waitingOn"))
                .as("a work segment is nobody's silence and carries no waiting-on answer")
                .isFalse();
        assertThat(stateOf(scene.requested())).isEqualTo("IN_PROGRESS");

        assertThat(jdbc.queryForObject(
                        "select count(*) from node_phase_row where work_node_id = ?::uuid"
                                + " and phase = 'EXTERNAL_WAIT' and ended_at is not null",
                        Long.class,
                        scene.requested()))
                .as("the wait was sealed rather than left running")
                .isEqualTo(1L);
        assertThat(phaseRowsOn(scene.requested()))
                .as("work, wait, work — three segments, and the resume opened a new row")
                .isEqualTo(3L);
        assertThat(jdbc.queryForObject(
                        "select count(*) from node_phase_row where work_node_id = ?::uuid and ended_at is null",
                        Long.class,
                        scene.requested()))
                .as("one clock runs at a time, or the rows read afterwards as work done during a wait")
                .isEqualTo(1L);

        assertThat(jdbc.queryForObject(
                        """
                        select count(*) from information_schema.columns
                        where table_name in ('work_node', 'node_phase_row')
                          and (column_name like '%total%'
                            or column_name like '%duration%'
                            or column_name like '%elapsed%')
                        """,
                        Long.class))
                .as("invariant I9 lives in the absence of a total, not in a convention somebody remembers")
                .isZero();
    }

    @Test
    void resumingWorkThatWasNeverBlockedIsRefusedCleanly() throws Exception {
        Company company = buildTheCompany();
        Scene scene = aRebrandingUnderway(company);

        ResponseEntity<String> refused = resume(browser, scene.solo());

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("ILLEGAL_NODE_TRANSITION");
        assertThat(stateOf(scene.solo())).isEqualTo("SELF");
        assertThat(phaseRowsOn(scene.solo()))
                .as("a refused transition opens no clock")
                .isZero();
    }

    @Test
    void theNudgeOffersThePersonsOwnOldestUnansweredWorkAndNobodyElsesWork() throws Exception {
        Company company = buildTheCompany();
        Scene scene = aRebrandingUnderway(company);

        ResponseEntity<String> asked = browser.get("/api/discovery/nudge");

        assertThat(asked.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode card = json.readTree(asked.getBody());
        assertThat(card.get("nodeId").asText())
                .as("her own work, and the oldest of it")
                .isEqualTo(scene.solo());
        assertThat(card.get("jobId").asText()).isEqualTo(scene.job());
        assertThat(card.get("text").asText())
                .as("the card names the thing rather than an identifier")
                .isEqualTo("Aurora Coffee vrea un rebranding complet");
        assertThat(card.get("state").asText()).isEqualTo("SELF");

        RoundTripClient elena = signedInBrowser("elena@atelier.ro");
        assertThat(elena.get("/api/discovery/nudge").getStatusCode())
                .as("204 is the ordinary answer; nobody is asked about work that is not theirs")
                .isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void aSecondNudgeIsRefusedBecauseOnceIsTheWholeOfIt() throws Exception {
        Company company = buildTheCompany();
        Scene scene = aRebrandingUnderway(company);

        ResponseEntity<String> stillGoing = answerNudge(browser, scene.solo(), "STILL_GOING");

        assertThat(stillGoing.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stateOf(scene.solo()))
                .as("still going moves nothing — the work carries on")
                .isEqualTo("SELF");
        assertThat(jdbc.queryForObject(
                        "select nudged_at is not null from work_node where id = ?::uuid", Boolean.class, scene.solo()))
                .isTrue();

        ResponseEntity<String> again = answerNudge(browser, scene.solo(), "DONE");

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(again.getBody()).get("code").asText()).isEqualTo("ALREADY_NUDGED");
        assertThat(browser.get("/api/discovery/nudge").getStatusCode())
                .as("and it is never offered again")
                .isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void droppedOnSoloWorkLapsesItAndSpendsTheOneNudge() throws Exception {
        Company company = buildTheCompany();
        Scene scene = aRebrandingUnderway(company);
        assertThat(stateOf(scene.solo())).isEqualTo("SELF");

        ResponseEntity<String> dropped = answerNudge(browser, scene.solo(), "DROPPED");

        assertThat(dropped.getStatusCode())
                .as("Dropped is one of four answers offered on every nudged node, in every state it can be nudged in")
                .isEqualTo(HttpStatus.OK);

        assertThat(stateOf(scene.solo()))
                .as("Self mirrors Assigned for every arrow that does not need a second person")
                .isEqualTo("LAPSED");
        assertThat(jdbc.queryForObject(
                        "select nudged_at from work_node where id = ?::uuid", String.class, scene.solo()))
                .as("an answered nudge is spent -- once per node, for all time, and she has answered")
                .isNotNull();
    }

    @Test
    void droppedOnWorkAskedOfSomebodyElseLapsesItAndRecordsNoDuration() throws Exception {
        Company company = buildTheCompany();
        Scene scene = aRebrandingUnderway(company);
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");

        ResponseEntity<String> dropped = answerNudge(andrei, scene.requested(), "DROPPED");

        assertThat(dropped.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(dropped.getBody()).get("state").asText()).isEqualTo("LAPSED");
        assertThat(stateOf(scene.requested())).isEqualTo("LAPSED");
        assertThat(outputTypeOf(scene.requested()))
                .as("a lapsed node contributes no output and none is inferred for it")
                .isNull();
        assertThat(jdbc.queryForObject(
                        "select closed_at from work_node where id = ?::uuid", String.class, scene.requested()))
                .as("Lapsed is terminal and is not a kind of closed")
                .isNull();
    }

    @Test
    void itWasAQuestionLeavesTheThreadAndIsOnlyEverAPersonSayingSo() throws Exception {
        Company company = buildTheCompany();
        Scene scene = aRebrandingUnderway(company);
        assertThat(jdbc.queryForObject(
                        "select track_id from work_node where id = ?::uuid", String.class, scene.requested()))
                .as("it was threaded when it was marked, which is what makes the removal observable")
                .isNotNull();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");

        ResponseEntity<String> recognised = answerNudge(andrei, scene.requested(), "WAS_A_QUESTION");

        assertThat(recognised.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stateOf(scene.requested())).isEqualTo("QUERY");
        assertThat(jdbc.queryForObject(
                        "select direction from work_node where id = ?::uuid", String.class, scene.requested()))
                .isEqualTo("QUERY");
        assertThat(jdbc.queryForObject(
                        "select track_id from work_node where id = ?::uuid", String.class, scene.requested()))
                .as("a question occupies no position in a thread — invariant I11")
                .isNull();
    }
}
