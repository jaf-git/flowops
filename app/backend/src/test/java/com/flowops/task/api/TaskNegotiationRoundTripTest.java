package com.flowops.task.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.RoundTripClient;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Tag("TASK-REJECT-01")
@Tag("TASK-PROPOSE-DEADLINE-01")
@Tag("TASK-DECIDE-DEADLINE-01")
@Tag("TASK-EDIT-01")
class TaskNegotiationRoundTripTest extends TaskScenarioTest {
    private static final String NOT_MINE = "This is Cristina's account, not mine";
    private static final String PARTS_ARRIVE_FRIDAY = "The parts arrive Friday";

    @Test
    void decliningReturnsTheWorkUnassignedAndSplitsTheWaitingInterval() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());

        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        ResponseEntity<String> declined =
                send(andrei, HttpMethod.POST, "/api/tasks/" + task + "/reject", Map.of("reason", NOT_MINE));

        assertThat(declined.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = json.readTree(declined.getBody());
        assertThat(body.get("state").asText()).isEqualTo("CREATED");
        assertThat(body.get("assigneeId").isNull())
                .as("the work belongs to nobody until somebody gives it out again")
                .isTrue();

        assertThat(assigneeOf(task)).isNull();
        assertThat(waitPhaseCount(task)).isEqualTo(2L);
        assertThat(openPhaseCount(task)).isEqualTo(1L);
        assertThat(openPhaseKind(task)).isEqualTo("WAIT");
        assertThat(transitionReason(task)).isEqualTo(NOT_MINE);
        assertThat(eventCount(task, "TASK_REJECTED")).isEqualTo(1L);
    }

    @Test
    void theAssignerCanStillSeeTheWorkThatCameBackToThem() throws Exception {
        Company company = buildTheCompany();

        RoundTripClient ionut = signedInBrowser("ionut@atelier.ro");
        String task = json.readTree(ionut.post("/api/tasks", body(company.andrei(), tomorrow()))
                        .getBody())
                .get("id")
                .asText();
        send(
                signedInBrowser("andrei@atelier.ro"),
                HttpMethod.POST,
                "/api/tasks/" + task + "/reject",
                Map.of("reason", NOT_MINE));

        ResponseEntity<String> queue = ionut.get("/api/tasks");

        JsonNode rows = json.readTree(queue.getBody()).get("tasks");
        JsonNode returned = rowFor(rows, task);
        assertThat(returned)
                .as("a rejected task is still visible to whoever gave it out")
                .isNotNull();
        assertThat(returned.get("assigneeId").isNull()).isTrue();
        assertThat(returned.get("assigneeName").asText())
                .as("empty, and the null identifier beside it is what tells this apart from an erased person")
                .isEmpty();
    }

    @Test
    void decliningSomebodyElsesWorkIsRefused() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());

        ResponseEntity<String> refused =
                send(browser, HttpMethod.POST, "/api/tasks/" + task + "/reject", Map.of("reason", NOT_MINE));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(codeOf(refused)).isEqualTo("NOT_THE_ASSIGNEE");
        assertThat(assigneeOf(task)).isEqualTo(company.andrei());
    }

    @Test
    void decliningWithNoReasonIsRefusedAtTheBoundary() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());

        ResponseEntity<String> refused = send(
                signedInBrowser("andrei@atelier.ro"),
                HttpMethod.POST,
                "/api/tasks/" + task + "/reject",
                Map.of("reason", "  "));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(codeOf(refused)).isEqualTo("REQUEST_INVALID");
        assertThat(assigneeOf(task)).isEqualTo(company.andrei());
    }

    @Test
    void decliningWorkAlreadyAcceptedIsRefusedAndNamesTheState() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        send(andrei, HttpMethod.POST, "/api/tasks/" + task + "/accept", Map.of());

        ResponseEntity<String> refused =
                send(andrei, HttpMethod.POST, "/api/tasks/" + task + "/reject", Map.of("reason", NOT_MINE));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codeOf(refused)).isEqualTo("ILLEGAL_TRANSITION");
        assertThat(refused.getBody()).contains("ACCEPTED");
    }

    @Test
    void proposingRecordsTheRequestAndLeavesTheTaskExactlyWhereItWas() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        Instant openedAt = openPhaseStartedAt(task);
        Instant asked = tomorrow().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);

        ResponseEntity<String> proposed = send(
                signedInBrowser("andrei@atelier.ro"),
                HttpMethod.POST,
                "/api/tasks/" + task + "/deadline-proposals",
                Map.of("proposedDeadline", asked.toString(), "reason", PARTS_ARRIVE_FRIDAY));

        assertThat(proposed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(proposed.getBody()).get("state").asText()).isEqualTo("CREATED");

        assertThat(openProposalCount(task)).isEqualTo(1L);
        assertThat(deadlineOf(task))
                .as("nothing about the date changes until somebody decides")
                .isNotEqualTo(asked);
        assertThat(openPhaseStartedAt(task))
                .as("the wait phase keeps running: a proposal may not be used to stop the clock")
                .isEqualTo(openedAt);
        assertThat(eventCount(task, "DEADLINE_PROPOSED")).isEqualTo(1L);
    }

    @Test
    void aSecondProposalIsRefusedAndNamesTheDateAlreadyAskedFor() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        Instant asked = tomorrow().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        send(
                andrei,
                HttpMethod.POST,
                "/api/tasks/" + task + "/deadline-proposals",
                Map.of("proposedDeadline", asked.toString(), "reason", PARTS_ARRIVE_FRIDAY));

        ResponseEntity<String> refused = send(
                andrei,
                HttpMethod.POST,
                "/api/tasks/" + task + "/deadline-proposals",
                Map.of("proposedDeadline", tomorrow().plus(14, ChronoUnit.DAYS).toString(), "reason", "Later still"));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codeOf(refused)).isEqualTo("PROPOSAL_ALREADY_OPEN");
        assertThat(Instant.parse(json.readTree(refused.getBody())
                        .get("details")
                        .get(0)
                        .get("rule")
                        .asText()))
                .as("the refusal names the date already asked for, so the person can tell whether it is theirs")
                .isEqualTo(asked);
        assertThat(openProposalCount(task)).isEqualTo(1L);
    }

    @Test
    void theProposalSurvivesTheAssigneeAcceptingTheWork() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        send(
                andrei,
                HttpMethod.POST,
                "/api/tasks/" + task + "/deadline-proposals",
                Map.of(
                        "proposedDeadline",
                        tomorrow().plus(7, ChronoUnit.DAYS).toString(),
                        "reason",
                        PARTS_ARRIVE_FRIDAY));

        ResponseEntity<String> accepted = send(andrei, HttpMethod.POST, "/api/tasks/" + task + "/accept", Map.of());

        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(openProposalCount(task)).isEqualTo(1L);
    }

    @Test
    void agreeingMovesTheDeadlineAndKeepsBothValuesOnTheRecord() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        Instant was = deadlineOf(task);
        Instant asked = tomorrow().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        send(
                signedInBrowser("andrei@atelier.ro"),
                HttpMethod.POST,
                "/api/tasks/" + task + "/deadline-proposals",
                Map.of("proposedDeadline", asked.toString(), "reason", PARTS_ARRIVE_FRIDAY));

        ResponseEntity<String> decided = send(
                browser,
                HttpMethod.POST,
                "/api/tasks/" + task + "/deadline-proposals/decision",
                Map.of("accept", true));

        assertThat(decided.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(deadlineOf(task)).isEqualTo(asked);
        assertThat(openProposalCount(task)).isZero();
        assertThat(proposalDecision(task)).isEqualTo("ACCEPTED");
        assertThat(amendmentFormerDeadline(task)).isEqualTo(was);
        assertThat(amendmentNewDeadline(task)).isEqualTo(asked);
        assertThat(eventCount(task, "DEADLINE_CHANGED")).isEqualTo(1L);
    }

    @Test
    void refusingLeavesTheDeadlineAloneAndKeepsTheAnswer() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        Instant was = deadlineOf(task);
        send(
                signedInBrowser("andrei@atelier.ro"),
                HttpMethod.POST,
                "/api/tasks/" + task + "/deadline-proposals",
                Map.of(
                        "proposedDeadline",
                        tomorrow().plus(7, ChronoUnit.DAYS).toString(),
                        "reason",
                        PARTS_ARRIVE_FRIDAY));

        ResponseEntity<String> decided = send(
                browser,
                HttpMethod.POST,
                "/api/tasks/" + task + "/deadline-proposals/decision",
                Map.of("accept", false, "reason", "The client will not move the audit"));

        assertThat(decided.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(deadlineOf(task)).isEqualTo(was);
        assertThat(proposalDecision(task)).isEqualTo("DECLINED");
        assertThat(amendmentCount(task))
                .as("the task never changed, so nothing records that it did")
                .isZero();
    }

    @Test
    void refusingInSilenceIsRefused() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        send(
                signedInBrowser("andrei@atelier.ro"),
                HttpMethod.POST,
                "/api/tasks/" + task + "/deadline-proposals",
                Map.of(
                        "proposedDeadline",
                        tomorrow().plus(7, ChronoUnit.DAYS).toString(),
                        "reason",
                        PARTS_ARRIVE_FRIDAY));

        ResponseEntity<String> refused = send(
                browser,
                HttpMethod.POST,
                "/api/tasks/" + task + "/deadline-proposals/decision",
                Map.of("accept", false, "reason", "  "));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(codeOf(refused)).isEqualTo("REQUEST_INVALID");
        assertThat(openProposalCount(task)).isEqualTo(1L);
    }

    @Test
    void decidingWhenNothingIsOpenIsRefused() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());

        ResponseEntity<String> refused = send(
                browser,
                HttpMethod.POST,
                "/api/tasks/" + task + "/deadline-proposals/decision",
                Map.of("accept", true));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codeOf(refused)).isEqualTo("NO_OPEN_PROPOSAL");
    }

    @Test
    void aManagerHoldingThePermissionCannotDecideAProposalOnWorkSomebodyElseAssigned() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        send(
                signedInBrowser("andrei@atelier.ro"),
                HttpMethod.POST,
                "/api/tasks/" + task + "/deadline-proposals",
                Map.of(
                        "proposedDeadline",
                        tomorrow().plus(7, ChronoUnit.DAYS).toString(),
                        "reason",
                        PARTS_ARRIVE_FRIDAY));

        ResponseEntity<String> refused = send(
                signedInBrowser("ionut@atelier.ro"),
                HttpMethod.POST,
                "/api/tasks/" + task + "/deadline-proposals/decision",
                Map.of("accept", true));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(codeOf(refused)).isEqualTo("NOT_THE_CREATOR");
        assertThat(openProposalCount(task)).isEqualTo(1L);
    }

    @Test
    void anEmployeeMayNotRedateOrRewriteWorkSomebodyElseCreated() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");

        ResponseEntity<String> decision = send(
                andrei, HttpMethod.POST, "/api/tasks/" + task + "/deadline-proposals/decision", Map.of("accept", true));
        ResponseEntity<String> edit = send(
                andrei,
                HttpMethod.PUT,
                "/api/tasks/" + task,
                Map.of("deadline", tomorrow().plus(3, ChronoUnit.DAYS).toString(), "priority", "HIGH"));

        assertThat(decision.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(codeOf(decision))
                .as("he holds the permission now; what he does not hold is authorship")
                .isEqualTo("NOT_THE_CREATOR");
        assertThat(edit.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(codeOf(edit)).isEqualTo("NOT_THE_CREATOR");
    }

    @Test
    void editingUpdatesTheFieldsAndRecordsWhatMoved() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        Instant was = deadlineOf(task);
        Instant moved = tomorrow().plus(10, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);

        ResponseEntity<String> edited = send(
                browser,
                HttpMethod.PUT,
                "/api/tasks/" + task,
                Map.of("deadline", moved.toString(), "priority", "URGENT", "description", "Include Cluj"));

        assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(deadlineOf(task)).isEqualTo(moved);
        assertThat(amendmentFormerDeadline(task)).isEqualTo(was);
        assertThat(amendmentNewDeadline(task)).isEqualTo(moved);
        assertThat(eventCount(task, "TASK_EDITED")).isEqualTo(1L);
    }

    @Test
    void aBodyThatTriesToChangeTheTitleOrTheAssigneeChangesNeither() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        String titleBefore = titleOf(task);

        ResponseEntity<String> edited = send(
                browser,
                HttpMethod.PUT,
                "/api/tasks/" + task,
                Map.of(
                        "deadline",
                        tomorrow().plus(10, ChronoUnit.DAYS).toString(),
                        "priority",
                        "HIGH",
                        "title",
                        "Something else entirely",
                        "assigneeId",
                        company.ionut().toString()));

        assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(titleOf(task)).isEqualTo(titleBefore);
        assertThat(assigneeOf(task)).isEqualTo(company.andrei());
    }

    @Test
    void submittingWhatItAlreadySaysIsRefusedAndAppendsNothing() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        Long eventsBefore = allEventCount(task);

        ResponseEntity<String> refused = send(
                browser,
                HttpMethod.PUT,
                "/api/tasks/" + task,
                Map.of(
                        "deadline",
                        deadlineOf(task).toString(),
                        "priority",
                        priorityOf(task),
                        "description",
                        descriptionOf(task)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(codeOf(refused)).isEqualTo("NOTHING_CHANGED");
        assertThat(allEventCount(task)).isEqualTo(eventsBefore);
        assertThat(amendmentCount(task)).isZero();
    }

    @Test
    void editingWorkUnderwayLeavesTheOpenIntervalUntouched() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        send(andrei, HttpMethod.POST, "/api/tasks/" + task + "/accept", Map.of());
        send(andrei, HttpMethod.POST, "/api/tasks/" + task + "/start", Map.of());
        UUID phaseBefore = openPhaseId(task);
        Instant startedBefore = openPhaseStartedAt(task);

        send(
                browser,
                HttpMethod.PUT,
                "/api/tasks/" + task,
                Map.of("deadline", tomorrow().plus(10, ChronoUnit.DAYS).toString(), "priority", "URGENT"));

        assertThat(stateOf(task)).isEqualTo("IN_PROGRESS");
        assertThat(openPhaseId(task))
                .as("the same row, not a closed one and a new one, which a count could not tell apart")
                .isEqualTo(phaseBefore);
        assertThat(openPhaseStartedAt(task)).isEqualTo(startedBefore);
    }

    @Test
    void editingAClosedTaskIsRefused() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        send(andrei, HttpMethod.POST, "/api/tasks/" + task + "/accept", Map.of());
        send(andrei, HttpMethod.POST, "/api/tasks/" + task + "/start", Map.of());
        send(andrei, HttpMethod.POST, "/api/tasks/" + task + "/complete", Map.of("note", "Done and sent."));
        send(browser, HttpMethod.POST, "/api/tasks/" + task + "/approve", Map.of("score", 4));
        send(browser, HttpMethod.POST, "/api/tasks/" + task + "/close", Map.of());

        ResponseEntity<String> refused = send(
                browser,
                HttpMethod.PUT,
                "/api/tasks/" + task,
                Map.of("deadline", tomorrow().plus(10, ChronoUnit.DAYS).toString(), "priority", "HIGH"));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codeOf(refused)).isEqualTo("TASK_IS_CLOSED");
    }

    @Test
    void shorteningTheDeadlineIntoTheWindowMakesTheWorkAtRisk() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());

        ResponseEntity<String> edited = send(
                browser,
                HttpMethod.PUT,
                "/api/tasks/" + task,
                Map.of("deadline", Instant.now().plus(30, ChronoUnit.MINUTES).toString(), "priority", "URGENT"));

        assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(edited.getBody()).get("atRisk").asBoolean())
                .as("half an hour from now is inside any plausible at-risk window")
                .isTrue();
    }

    @Test
    void wideningTheWindowMakesApproachingWorkAtRiskWithoutAnybodyTouchingIt() throws Exception {
        Company company = buildTheCompany();
        ResponseEntity<String> created =
                browser.post("/api/tasks", body(company.andrei(), Instant.now().plus(48, ChronoUnit.HOURS)));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String task = json.readTree(created.getBody()).get("id").asText();
        assertThat(atRiskInTheQueue(task))
                .as("two days out, against the default twenty-four hour window")
                .isFalse();

        assertThat(send(browser, HttpMethod.PUT, "/api/workspace/settings", settingsWithWindowOf(72))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(atRiskInTheQueue(task))
                .as("the same task, now inside the window the owner just set")
                .isTrue();
    }

    private static Map<String, Object> settingsWithWindowOf(int hours) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("name", "Atelier Ionescu");
        payload.put("use", "WORK");
        payload.put("timezone", "Europe/Bucharest");
        payload.put("workingDays", java.util.List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"));
        payload.put("workingHoursStart", "09:00");
        payload.put("workingHoursEnd", "17:00");
        payload.put("atRiskWindowHours", hours);
        payload.put("escalationIntervalsHours", java.util.List.of(24, 72, 168));
        payload.put("quietHoursStart", "22:00");
        payload.put("quietHoursEnd", "06:00");
        payload.put("invitationApprovalRequired", false);
        return payload;
    }

    @Test
    void nobodyAcknowledgesOnTheCreatorsBehalfNotEvenTheOwner() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient ionut = signedInBrowser("ionut@atelier.ro");

        ResponseEntity<String> created = ionut.post(
                "/api/tasks",
                "{\"title\":\"Pregătește dosarul fiscal\",\"assigneeId\":\"%s\"}".formatted(company.andrei()));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String task = json.readTree(created.getBody()).get("id").asText();

        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        andrei.post("/api/tasks/" + task + "/accept", "");
        andrei.post(
                "/api/tasks/" + task + "/deadline",
                "{\"deadline\":\"%s\"}".formatted(Instant.now().plus(72, ChronoUnit.HOURS)));

        ResponseEntity<String> refused = browser.post("/api/tasks/" + task + "/deadline-notice/acknowledge", "");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("NOT_THE_CREATOR");

        assertThat(json.readTree(ionut.get("/api/tasks/deadline-notices").getBody())
                        .get("notices"))
                .hasSize(1);
    }

    private boolean atRiskInTheQueue(String task) throws Exception {
        for (JsonNode row : json.readTree(browser.get("/api/tasks").getBody()).get("tasks")) {
            if (row.get("id").asText().equals(task)) {
                return row.get("atRisk").asBoolean();
            }
        }
        throw new AssertionError("the task is not in the queue at all, so the assertion proved nothing");
    }

    @Test
    void aManagerEditsTheWorkTheyAssignedThemselves() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient ionut = signedInBrowser("ionut@atelier.ro");
        ResponseEntity<String> created = send(
                ionut, HttpMethod.POST, "/api/tasks", json.readValue(body(company.andrei(), tomorrow()), Map.class));
        String task = json.readTree(created.getBody()).get("id").asText();

        ResponseEntity<String> edited = send(
                ionut,
                HttpMethod.PUT,
                "/api/tasks/" + task,
                Map.of("deadline", tomorrow().plus(5, ChronoUnit.DAYS).toString(), "priority", "HIGH"));

        assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void theQueueTellsTheAssignerWhichWorkIsTheirsToDirect() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient ionut = signedInBrowser("ionut@atelier.ro");
        String mine = json.readTree(ionut.post("/api/tasks", body(company.andrei(), tomorrow()))
                        .getBody())
                .get("id")
                .asText();
        String hers = createTaskFor(company.andrei());

        JsonNode rows = json.readTree(
                        send(ionut, HttpMethod.GET, "/api/tasks", Map.of()).getBody())
                .get("tasks");

        assertThat(rowFor(rows, mine).get("directedByMe").asBoolean())
                .as("Ionut gave this out, so it is his to edit")
                .isTrue();
        assertThat(rowFor(rows, hers).get("directedByMe").asBoolean())
                .as("Maria gave this out; Ionut reaches it through the tree and may not retarget it")
                .isFalse();
    }

    @Test
    void theQueueSaysWhenSomebodyIsWaitingOnAnAnswerAboutTheDate() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient ionut = signedInBrowser("ionut@atelier.ro");
        String task = json.readTree(ionut.post("/api/tasks", body(company.andrei(), tomorrow()))
                        .getBody())
                .get("id")
                .asText();

        JsonNode before = rowFor(
                json.readTree(send(ionut, HttpMethod.GET, "/api/tasks", Map.of())
                                .getBody())
                        .get("tasks"),
                task);
        assertThat(before.get("deadlineProposalOpen").asBoolean())
                .as("nobody has asked for anything yet")
                .isFalse();

        send(
                signedInBrowser("andrei@atelier.ro"),
                HttpMethod.POST,
                "/api/tasks/" + task + "/deadline-proposals",
                Map.of(
                        "proposedDeadline",
                        tomorrow().plus(7, ChronoUnit.DAYS).toString(),
                        "reason",
                        PARTS_ARRIVE_FRIDAY));

        JsonNode after = rowFor(
                json.readTree(send(ionut, HttpMethod.GET, "/api/tasks", Map.of())
                                .getBody())
                        .get("tasks"),
                task);
        assertThat(after.get("deadlineProposalOpen").asBoolean())
                .as("Andrei is waiting on Ionut, and the row is the only place that says so")
                .isTrue();
    }

    @Test
    void theDetailCarriesWhatWasAskedForAndWhy() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        Instant asked = tomorrow().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        send(
                signedInBrowser("andrei@atelier.ro"),
                HttpMethod.POST,
                "/api/tasks/" + task + "/deadline-proposals",
                Map.of("proposedDeadline", asked.toString(), "reason", PARTS_ARRIVE_FRIDAY));

        JsonNode detail = json.readTree(
                send(browser, HttpMethod.GET, "/api/tasks/" + task, Map.of()).getBody());

        JsonNode proposal = detail.get("deadlineProposal");
        assertThat(proposal.isNull())
                .as("there is an open proposal and the detail must carry it")
                .isFalse();
        assertThat(Instant.parse(proposal.get("proposedDeadline").asText())).isEqualTo(asked);
        assertThat(proposal.get("reason").asText()).isEqualTo(PARTS_ARRIVE_FRIDAY);
    }

    @Test
    void theAssignerCanOpenTheWorkThatCameBackToThemInFull() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient ionut = signedInBrowser("ionut@atelier.ro");
        String task = json.readTree(ionut.post("/api/tasks", body(company.andrei(), tomorrow()))
                        .getBody())
                .get("id")
                .asText();
        send(
                signedInBrowser("andrei@atelier.ro"),
                HttpMethod.POST,
                "/api/tasks/" + task + "/reject",
                Map.of("reason", NOT_MINE));

        ResponseEntity<String> detail = send(ionut, HttpMethod.GET, "/api/tasks/" + task, Map.of());

        assertThat(detail.getStatusCode())
                .as("he assigned it, and nobody holds it now, so no other clause can let him in")
                .isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(detail.getBody()).get("assigneeId").isNull()).isTrue();
    }

    @Test
    void decliningTheWorkAlsoClosesAnyDateStillBeingAskedAbout() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        send(
                andrei,
                HttpMethod.POST,
                "/api/tasks/" + task + "/deadline-proposals",
                Map.of(
                        "proposedDeadline",
                        tomorrow().plus(7, ChronoUnit.DAYS).toString(),
                        "reason",
                        PARTS_ARRIVE_FRIDAY));

        send(andrei, HttpMethod.POST, "/api/tasks/" + task + "/reject", Map.of("reason", NOT_MINE));

        assertThat(openProposalCount(task))
                .as("nobody holds the work, so nobody is owed an answer about its date")
                .isZero();
        assertThat(proposalDecision(task)).isEqualTo("DECLINED");

        ResponseEntity<String> decided = send(
                browser,
                HttpMethod.POST,
                "/api/tasks/" + task + "/deadline-proposals/decision",
                Map.of("accept", true));
        assertThat(decided.getStatusCode())
                .as("and answering it afterwards is refused rather than crashing after the commit")
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(codeOf(decided)).isEqualTo("NO_OPEN_PROPOSAL");
    }

    private String codeOf(ResponseEntity<String> response) throws Exception {
        return json.readTree(response.getBody()).get("code").asText();
    }

    private JsonNode rowFor(JsonNode rows, String task) {
        for (JsonNode row : rows) {
            if (row.get("id").asText().equals(task)) {
                return row;
            }
        }
        return null;
    }

    private UUID assigneeOf(String task) {
        return jdbc.queryForObject("select assignee_user_id from task where id = ?::uuid", UUID.class, task);
    }

    private String titleOf(String task) {
        return jdbc.queryForObject("select title from task where id = ?::uuid", String.class, task);
    }

    private String descriptionOf(String task) {
        return jdbc.queryForObject("select description from task where id = ?::uuid", String.class, task);
    }

    private String priorityOf(String task) {
        return jdbc.queryForObject("select priority from task where id = ?::uuid", String.class, task);
    }

    private String stateOf(String task) {
        return jdbc.queryForObject("select state from task where id = ?::uuid", String.class, task);
    }

    private Instant deadlineOf(String task) {
        return jdbc.queryForObject("select deadline from task where id = ?::uuid", Instant.class, task);
    }

    private Long waitPhaseCount(String task) {
        return jdbc.queryForObject(
                "select count(*) from task_phase_timer where task_id = ?::uuid and phase_kind = 'WAIT'",
                Long.class,
                task);
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

    private UUID openPhaseId(String task) {
        return jdbc.queryForObject(
                "select id from task_phase_timer where task_id = ?::uuid and ended_at is null", UUID.class, task);
    }

    private Instant openPhaseStartedAt(String task) {
        return jdbc.queryForObject(
                "select started_at from task_phase_timer where task_id = ?::uuid and ended_at is null",
                Instant.class,
                task);
    }

    private String transitionReason(String task) {
        return jdbc.queryForObject(
                "select reason from task_state_transition where task_id = ?::uuid and reason is not null"
                        + " order by occurred_at desc limit 1",
                String.class,
                task);
    }

    private Long openProposalCount(String task) {
        return jdbc.queryForObject(
                "select count(*) from task_deadline_proposal where task_id = ?::uuid and decision is null",
                Long.class,
                task);
    }

    private String proposalDecision(String task) {
        return jdbc.queryForObject(
                "select decision from task_deadline_proposal where task_id = ?::uuid order by proposed_at desc limit 1",
                String.class,
                task);
    }

    private Long amendmentCount(String task) {
        return jdbc.queryForObject("select count(*) from task_amendment where task_id = ?::uuid", Long.class, task);
    }

    private Instant amendmentFormerDeadline(String task) {
        return jdbc.queryForObject(
                "select former_deadline from task_amendment where task_id = ?::uuid order by occurred_at desc limit 1",
                Instant.class,
                task);
    }

    private Instant amendmentNewDeadline(String task) {
        return jdbc.queryForObject(
                "select new_deadline from task_amendment where task_id = ?::uuid order by occurred_at desc limit 1",
                Instant.class,
                task);
    }

    private Long eventCount(String task, String action) {
        return jdbc.queryForObject(
                "select count(*) from task_event where task_id = ?::uuid and action = ?", Long.class, task, action);
    }

    private Long allEventCount(String task) {
        return jdbc.queryForObject("select count(*) from task_event where task_id = ?::uuid", Long.class, task);
    }

    private ResponseEntity<String> send(RoundTripClient client, HttpMethod method, String path, Map<String, ?> body)
            throws Exception {
        return client.exchange(method, path, json.writeValueAsString(body));
    }
}
