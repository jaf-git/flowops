package com.flowops.task.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.RoundTripClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("TASK-REVIEW-01")
@Tag("TASK-APPROVE-01")
@Tag("TASK-REJECT-IN-REVIEW-01")
@Tag("TASK-CLOSE-01")
class TaskReviewRoundTripTest extends TaskScenarioTest {
    private static final String NOTE = "Compared both quarters and sent the summary to the board.";
    private static final String REASON = "The figures for March are missing from the second table.";
    private static final String LINK = "https://drive.atelier.ro/q3-supplier-review";

    @Test
    void theOwnerSeesCompletedWorkWaitingOnHerJudgement() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        completeAsAndrei(task);

        ResponseEntity<String> queue = browser.get("/api/tasks/review-queue");

        assertThat(queue.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode rows = json.readTree(queue.getBody()).get("tasks");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("id").asText()).isEqualTo(task);
        assertThat(rows.get(0).get("openPhase").asText())
                .as("the waiting time the queue prints is the reviewer's own review phase")
                .isEqualTo("REVIEW");
        assertThat(rows.get(0).get("phaseSince").isNull()).isFalse();
    }

    @Test
    void workThatIsStillBeingDoneIsNotInTheReviewQueue() throws Exception {
        Company company = buildTheCompany();
        String underway = createTaskFor(company.andrei());
        startAsAndrei(underway);

        assertThat(json.readTree(browser.get("/api/tasks/review-queue").getBody())
                        .get("tasks"))
                .isEmpty();
    }

    @Test
    void theQueuePutsTheWorkThatHasWaitedLongestFirst() throws Exception {
        Company company = buildTheCompany();
        String first = createTaskFor(company.andrei());
        String second = createTaskFor(company.andrei());
        completeAsAndrei(first);
        completeAsAndrei(second);

        JsonNode rows =
                json.readTree(browser.get("/api/tasks/review-queue").getBody()).get("tasks");

        assertThat(List.of(rows.get(0).get("id").asText(), rows.get(1).get("id").asText()))
                .containsExactly(first, second);
    }

    @Test
    void aManagerSeesOnlyTheWorkOfPeopleBeneathThem() throws Exception {
        Company company = buildTheCompany();
        String andreis = createTaskFor(company.andrei());
        String elenas = createTaskFor(company.elena());
        completeAsAndrei(andreis);
        complete(signedInBrowser("elena@atelier.ro"), elenas);

        JsonNode rows = json.readTree(signedInBrowser("ionut@atelier.ro")
                        .get("/api/tasks/review-queue")
                        .getBody())
                .get("tasks");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("id").asText()).isEqualTo(andreis);
    }

    @Test
    void aReportingLineChangeShowsUpInTheQueueImmediately() throws Exception {
        Company company = buildTheCompany();
        String elenas = createTaskFor(company.elena());
        complete(signedInBrowser("elena@atelier.ro"), elenas);
        RoundTripClient ionut = signedInBrowser("ionut@atelier.ro");
        assertThat(json.readTree(ionut.get("/api/tasks/review-queue").getBody()).get("tasks"))
                .isEmpty();

        jdbc.update(
                "update workspace_membership set manager_id = (select id from workspace_membership where user_id = ?)"
                        + " where user_id = ?",
                company.ionut(),
                company.elena());

        assertThat(json.readTree(ionut.get("/api/tasks/review-queue").getBody()).get("tasks"))
                .hasSize(1);
    }

    @Test
    void anEmployeeHasNoReviewQueueToOpen() throws Exception {
        buildTheCompany();

        ResponseEntity<String> refused = signedInBrowser("andrei@atelier.ro").get("/api/tasks/review-queue");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("NOT_PERMITTED");
    }

    @Test
    void anEmptyQueueIsAnEmptyListRatherThanAnError() throws Exception {
        buildTheCompany();

        ResponseEntity<String> queue = browser.get("/api/tasks/review-queue");

        assertThat(queue.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(queue.getBody()).get("tasks")).isEmpty();
    }

    @Test
    void aManagerSeesTheirOwnCompletedWorkInTheirQueue() throws Exception {
        Company company = buildTheCompany();
        String ionuts = createTaskFor(company.ionut());
        complete(signedInBrowser("ionut@atelier.ro"), ionuts);

        JsonNode rows = json.readTree(signedInBrowser("ionut@atelier.ro")
                        .get("/api/tasks/review-queue")
                        .getBody())
                .get("tasks");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("mine").asBoolean()).isTrue();
    }

    @Test
    void openingACompletedTaskShowsWhatWasDeliveredAndWhereTheTimeWent() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        completeAsAndrei(task);

        ResponseEntity<String> detail = browser.get("/api/tasks/" + task);

        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = json.readTree(detail.getBody());
        assertThat(body.get("proof").get("note").asText()).isEqualTo(NOTE);
        assertThat(body.get("proof").get("externalLink").asText()).isEqualTo(LINK);
        assertThat(body.get("deadlineMet").asBoolean()).isTrue();
        assertThat(body.get("approval").isNull()).isTrue();
        assertThat(body.get("openPhase").asText()).isEqualTo("REVIEW");
        assertThat(body.get("phases")).isNotEmpty();
        assertThat(body.has("totalSeconds"))
                .as("there is no total, deliberately: an unqualified duration is a defect")
                .isFalse();
    }

    @Test
    void lookingAtSomebodysWorkLeavesNoTraceOnTheirRecord() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        completeAsAndrei(task);
        long eventsBefore = allEventCount(task);

        browser.get("/api/tasks/" + task);
        browser.get("/api/tasks/review-queue");

        assertThat(allEventCount(task)).isEqualTo(eventsBefore);
    }

    @Test
    void approvingMovesTheWorkOnAndStoresTheJudgement() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        completeAsAndrei(task);

        ResponseEntity<String> approved = browser.post(
                "/api/tasks/%s/approve".formatted(task), "{\"score\":4,\"comment\":\"Clear and on time.\"}");

        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(approved.getBody()).get("state").asText()).isEqualTo("APPROVED");
        assertThat(stateOf(task)).isEqualTo("APPROVED");

        Map<String, Object> stored = jdbc.queryForMap(
                "select score, comment, reviewer_user_id from task_approval where task_id = ?::uuid", task);
        assertThat(((Number) stored.get("score")).intValue()).isEqualTo(4);
        assertThat(stored.get("comment")).isEqualTo("Clear and on time.");
        assertThat(stored.get("reviewer_user_id")).isEqualTo(company.maria());

        assertThat(openPhaseKind(task)).isEqualTo("APPROVAL");
        assertThat(openPhaseCount(task)).isEqualTo(1L);
        assertThat(closedPhaseCount(task, "REVIEW")).isEqualTo(1L);
        assertThat(eventCount(task, "TASK_APPROVED")).isEqualTo(1L);
        assertThat(transitionCount(task, "COMPLETED", "APPROVED")).isEqualTo(1L);
    }

    @Test
    void aManagerCannotApproveTheWorkThatWasGivenToHim() throws Exception {
        Company company = buildTheCompany();
        String ionuts = createTaskFor(company.ionut());
        complete(signedInBrowser("ionut@atelier.ro"), ionuts);

        ResponseEntity<String> refused =
                signedInBrowser("ionut@atelier.ro").post("/api/tasks/%s/approve".formatted(ionuts), "{\"score\":5}");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("CANNOT_REVIEW_OWN_WORK");
        assertThat(stateOf(ionuts)).isEqualTo("COMPLETED");
        assertThat(approvalCount(ionuts)).isZero();
    }

    @Test
    void aManagerCannotApproveWorkOutsideTheirOwnTeam() throws Exception {
        Company company = buildTheCompany();
        String elenas = createTaskFor(company.elena());
        complete(signedInBrowser("elena@atelier.ro"), elenas);

        ResponseEntity<String> refused =
                signedInBrowser("ionut@atelier.ro").post("/api/tasks/%s/approve".formatted(elenas), "{\"score\":5}");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("TASK_OUT_OF_SCOPE");
        assertThat(approvalCount(elenas)).isZero();
    }

    @Test
    void anEmployeeCannotApproveAnything() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        completeAsAndrei(task);

        ResponseEntity<String> refused =
                signedInBrowser("andrei@atelier.ro").post("/api/tasks/%s/approve".formatted(task), "{\"score\":5}");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("NOT_PERMITTED");
    }

    @Test
    void approvingWithNoScoreIsRefusedAndNothingMoves() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        completeAsAndrei(task);

        ResponseEntity<String> refused = browser.post("/api/tasks/%s/approve".formatted(task), "{}");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("REQUEST_INVALID");
        assertThat(stateOf(task)).isEqualTo("COMPLETED");
        assertThat(approvalCount(task)).isZero();
    }

    @Test
    void approvingWithAScoreOutsideOneToFiveIsRefused() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        completeAsAndrei(task);

        ResponseEntity<String> refused = browser.post("/api/tasks/%s/approve".formatted(task), "{\"score\":9}");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(approvalCount(task)).isZero();
    }

    @Test
    void approvingWorkThatWasNeverSubmittedIsRefusedAndNamesItsState() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        startAsAndrei(task);

        ResponseEntity<String> refused = browser.post("/api/tasks/%s/approve".formatted(task), "{\"score\":4}");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode body = json.readTree(refused.getBody());
        assertThat(body.get("code").asText()).isEqualTo("ILLEGAL_TRANSITION");
        assertThat(body.get("details").get(0).get("rule").asText()).isEqualTo("IN_PROGRESS");
        assertThat(approvalCount(task)).isZero();
    }

    @Test
    void sendingWorkBackReturnsItToInProgressAndOpensANewClock() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        completeAsAndrei(task);
        UUID firstActive = activePhaseIds(task).get(0);

        ResponseEntity<String> returned =
                browser.post("/api/tasks/%s/return".formatted(task), "{\"reason\":\"%s\"}".formatted(REASON));

        assertThat(returned.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(returned.getBody()).get("state").asText()).isEqualTo("IN_PROGRESS");
        assertThat(stateOf(task))
                .as("extension 2a: back to InProgress and never to Created")
                .isEqualTo("IN_PROGRESS");
        assertThat(assigneeOf(task))
                .as("the assignee keeps the work; a return is not a reassignment")
                .isEqualTo(company.andrei());
        assertThat(transitionReason(task, "COMPLETED", "IN_PROGRESS")).isEqualTo(REASON);
        assertThat(eventCount(task, "TASK_RETURNED_FOR_REWORK")).isEqualTo(1L);
        assertThat(approvalCount(task))
                .as("extension 2c: a return records no score")
                .isZero();

        List<UUID> actives = activePhaseIds(task);
        assertThat(actives).hasSize(2);
        assertThat(actives.get(1)).as("a new row, never the closed one resumed").isNotEqualTo(firstActive);
        assertThat(jdbc.queryForObject(
                        "select ended_at is not null from task_phase_timer where id = ?", Boolean.class, firstActive))
                .as("the earlier active row stays closed and is never touched again")
                .isTrue();
    }

    @Test
    void workReturnedTwiceKeepsBothRoundsOnTheRecord() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        completeAsAndrei(task);
        sendBack(task);
        complete(signedInBrowser("andrei@atelier.ro"), task);
        sendBack(task);

        assertThat(closedPhaseCount(task, "REVIEW")).isEqualTo(2L);
        assertThat(activePhaseIds(task)).hasSize(3);
        assertThat(transitionCount(task, "COMPLETED", "IN_PROGRESS")).isEqualTo(2L);
        assertThat(approvalCount(task)).isZero();
    }

    @Test
    void aManagerCannotSendBackTheWorkThatWasGivenToHim() throws Exception {
        Company company = buildTheCompany();
        String ionuts = createTaskFor(company.ionut());
        complete(signedInBrowser("ionut@atelier.ro"), ionuts);

        ResponseEntity<String> refused = signedInBrowser("ionut@atelier.ro")
                .post("/api/tasks/%s/return".formatted(ionuts), "{\"reason\":\"%s\"}".formatted(REASON));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("CANNOT_REVIEW_OWN_WORK");
        assertThat(stateOf(ionuts)).isEqualTo("COMPLETED");
    }

    @Test
    void sendingWorkBackWithNoReasonIsRefused() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        completeAsAndrei(task);

        ResponseEntity<String> refused = browser.post("/api/tasks/%s/return".formatted(task), "{\"reason\":\"   \"}");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("REQUEST_INVALID");
        assertThat(stateOf(task)).isEqualTo("COMPLETED");
    }

    @Test
    void closingApprovedWorkStopsEveryClockOnIt() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        completeAsAndrei(task);
        approve(task);

        ResponseEntity<String> closed = browser.post("/api/tasks/%s/close".formatted(task), "");

        assertThat(closed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(closed.getBody()).get("state").asText()).isEqualTo("CLOSED");
        assertThat(stateOf(task)).isEqualTo("CLOSED");
        assertThat(openPhaseCount(task))
                .as("no interval is accruing on a closed task, against anybody")
                .isZero();
        assertThat(closedPhaseCount(task, "APPROVAL")).isEqualTo(1L);
        assertThat(eventCount(task, "TASK_CLOSED")).isEqualTo(1L);
    }

    @Test
    void aClosedTaskKeepsEveryPhaseTransitionProofAndApproval() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        completeAsAndrei(task);
        approve(task);
        browser.post("/api/tasks/%s/close".formatted(task), "");

        JsonNode body = json.readTree(browser.get("/api/tasks/" + task).getBody());

        assertThat(body.get("proof").get("note").asText()).isEqualTo(NOTE);
        assertThat(body.get("approval").get("score").asInt()).isEqualTo(4);
        assertThat(body.get("phases")).isNotEmpty();
        assertThat(body.get("openPhase").isNull()).isTrue();
        assertThat(transitionCount(task, "APPROVED", "CLOSED")).isEqualTo(1L);
    }

    @Test
    void closingWorkThatWasNeverApprovedIsRefusedAndNamesItsState() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        completeAsAndrei(task);

        ResponseEntity<String> refused = browser.post("/api/tasks/%s/close".formatted(task), "");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(refused.getBody())
                        .get("details")
                        .get(0)
                        .get("rule")
                        .asText())
                .isEqualTo("COMPLETED");
        assertThat(stateOf(task)).isEqualTo("COMPLETED");
    }

    @Test
    void closingAnAlreadyClosedTaskIsRefusedAndAppendsNothing() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        completeAsAndrei(task);
        approve(task);
        browser.post("/api/tasks/%s/close".formatted(task), "");

        ResponseEntity<String> refused = browser.post("/api/tasks/%s/close".formatted(task), "");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(refused.getBody())
                        .get("details")
                        .get(0)
                        .get("rule")
                        .asText())
                .isEqualTo("CLOSED");
        assertThat(eventCount(task, "TASK_CLOSED")).isEqualTo(1L);
    }

    @Test
    void anEmployeeCannotCloseTheirOwnApprovedWork() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        completeAsAndrei(task);
        approve(task);

        ResponseEntity<String> refused =
                signedInBrowser("andrei@atelier.ro").post("/api/tasks/%s/close".formatted(task), "");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("NOT_PERMITTED");
        assertThat(stateOf(task)).isEqualTo("APPROVED");
    }

    private void completeAsAndrei(String task) {
        complete(signedInBrowser("andrei@atelier.ro"), task);
    }

    private void startAsAndrei(String task) {
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        assertThat(andrei.post("/api/tasks/%s/accept".formatted(task), "").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(andrei.post("/api/tasks/%s/start".formatted(task), "").getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    private void complete(RoundTripClient client, String task) {
        if ("CREATED".equals(stateOf(task))) {
            assertThat(client.post("/api/tasks/%s/accept".formatted(task), "").getStatusCode())
                    .isEqualTo(HttpStatus.OK);
        }
        if ("ACCEPTED".equals(stateOf(task))) {
            assertThat(client.post("/api/tasks/%s/start".formatted(task), "").getStatusCode())
                    .isEqualTo(HttpStatus.OK);
        }
        assertThat(client.post(
                                "/api/tasks/%s/complete".formatted(task),
                                "{\"note\":\"%s\",\"externalLink\":\"%s\"}".formatted(NOTE, LINK))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    private void approve(String task) {
        assertThat(browser.post("/api/tasks/%s/approve".formatted(task), "{\"score\":4,\"comment\":\"Good.\"}")
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    private void sendBack(String task) {
        assertThat(browser.post("/api/tasks/%s/return".formatted(task), "{\"reason\":\"%s\"}".formatted(REASON))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    private String stateOf(String task) {
        return jdbc.queryForObject("select state from task where id = ?::uuid", String.class, task);
    }

    private UUID assigneeOf(String task) {
        return jdbc.queryForObject("select assignee_user_id from task where id = ?::uuid", UUID.class, task);
    }

    private Long openPhaseCount(String task) {
        return jdbc.queryForObject(
                "select count(*) from task_phase_timer where task_id = ?::uuid and ended_at is null", Long.class, task);
    }

    private String openPhaseKind(String task) {
        return jdbc.queryForObject(
                "select phase_kind from task_phase_timer where task_id = ?::uuid and ended_at is null",
                String.class,
                task);
    }

    private Long closedPhaseCount(String task, String kind) {
        return jdbc.queryForObject(
                "select count(*) from task_phase_timer where task_id = ?::uuid and phase_kind = ?"
                        + " and ended_at is not null",
                Long.class,
                task,
                kind);
    }

    private List<UUID> activePhaseIds(String task) {
        return jdbc.queryForList(
                "select id from task_phase_timer where task_id = ?::uuid and phase_kind = 'ACTIVE'"
                        + " order by started_at",
                UUID.class,
                task);
    }

    private Long approvalCount(String task) {
        return jdbc.queryForObject("select count(*) from task_approval where task_id = ?::uuid", Long.class, task);
    }

    private Long transitionCount(String task, String from, String to) {
        return jdbc.queryForObject(
                "select count(*) from task_state_transition where task_id = ?::uuid"
                        + " and from_state = ? and to_state = ?",
                Long.class,
                task,
                from,
                to);
    }

    private String transitionReason(String task, String from, String to) {
        return jdbc.queryForObject(
                "select reason from task_state_transition where task_id = ?::uuid and from_state = ? and to_state = ?"
                        + " order by occurred_at desc limit 1",
                String.class,
                task,
                from,
                to);
    }

    private Long eventCount(String task, String action) {
        return jdbc.queryForObject(
                "select count(*) from task_event where task_id = ?::uuid and action = ?", Long.class, task, action);
    }

    private Long allEventCount(String task) {
        return jdbc.queryForObject("select count(*) from task_event where task_id = ?::uuid", Long.class, task);
    }

    @Test
    void aManagerApprovesTheWorkOfSomebodyTwoLevelsBeneathThem() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        completeAsAndrei(task);

        ResponseEntity<String> approved = signedInBrowser("ionut@atelier.ro")
                .post("/api/tasks/%s/approve".formatted(task), "{\"score\":4,\"comment\":\"Clear.\"}");

        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stateOf(task)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject(
                        "select reviewer_user_id from task_approval where task_id = ?::uuid", UUID.class, task))
                .as("the judgement is recorded against the manager who made it")
                .isEqualTo(company.ionut());
    }

    @Test
    void aManagerSendsBackTheWorkOfSomebodyBeneathThem() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        completeAsAndrei(task);

        ResponseEntity<String> returned = signedInBrowser("ionut@atelier.ro")
                .post("/api/tasks/%s/return".formatted(task), "{\"reason\":\"%s\"}".formatted(REASON));

        assertThat(returned.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stateOf(task)).isEqualTo("IN_PROGRESS");
    }

    @Test
    void aManagerClosesTheApprovedWorkOfSomebodyBeneathThem() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        completeAsAndrei(task);
        approve(task);

        ResponseEntity<String> closed =
                signedInBrowser("ionut@atelier.ro").post("/api/tasks/%s/close".formatted(task), "");

        assertThat(closed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stateOf(task)).isEqualTo("CLOSED");
        assertThat(openPhaseCount(task)).isZero();
    }

    @Test
    void aManagerOpensTheWorkOfSomebodyBeneathThem() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        completeAsAndrei(task);

        ResponseEntity<String> detail = signedInBrowser("ionut@atelier.ro").get("/api/tasks/" + task);

        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(detail.getBody()).get("proof").get("note").asText())
                .isEqualTo(NOTE);
    }
}
