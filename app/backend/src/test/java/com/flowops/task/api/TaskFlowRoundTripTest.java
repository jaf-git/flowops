package com.flowops.task.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.RoundTripClient;
import com.flowops.task.domain.model.CompletionProof;
import com.flowops.task.domain.model.TaskId;
import com.flowops.task.infrastructure.persistence.CompletionProofPersistenceAdapter;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("TASK-START-01")
@Tag("TASK-BLOCK-01")
@Tag("TASK-UNBLOCK-01")
@Tag("TASK-COMPLETE-01")
class TaskFlowRoundTripTest extends TaskScenarioTest {
    private static final String REASON = "The supplier has not sent last quarter's figures";
    private static final String NOTE = "Compared both quarters and sent the summary to the board.";

    @Autowired
    private CompletionProofPersistenceAdapter completionProofs;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void theAssigneeStartsAndTheFirstActivePhaseInTheProductOpens() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        accept(andrei, task);

        ResponseEntity<String> started = andrei.post("/api/tasks/%s/start".formatted(task), "");

        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(started.getBody()).get("state").asText()).isEqualTo("IN_PROGRESS");
        assertThat(stateOf(task)).isEqualTo("IN_PROGRESS");

        Map<String, Object> open = jdbc.queryForMap(
                "select phase_kind, started_at from task_phase_timer where task_id = ?::uuid and ended_at is null",
                task);
        assertThat(open.get("phase_kind"))
                .as("the only interval in the product attributed to a person")
                .isEqualTo("ACTIVE");
        assertThat(openPhaseCount(task))
                .as("exactly one phase is open at any moment")
                .isEqualTo(1L);

        assertThat(transitionCount(task, "ACCEPTED", "IN_PROGRESS")).isEqualTo(1L);
        assertThat(eventCount(task, "TASK_STARTED")).isEqualTo(1L);
    }

    @Test
    void startingWorkThatWasNeverAcknowledgedIsRefusedAndNamesTheState() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());

        ResponseEntity<String> refused =
                signedInBrowser("andrei@atelier.ro").post("/api/tasks/%s/start".formatted(task), "");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode body = json.readTree(refused.getBody());
        assertThat(body.get("code").asText()).isEqualTo("ILLEGAL_TRANSITION");
        assertThat(body.get("details").get(0).get("rule").asText()).isEqualTo("CREATED");
        assertThat(activePhaseCount(task))
                .as("a refused start opens no clock against anybody")
                .isZero();
    }

    @Test
    void aManagerCannotStartWorkOnSomebodyElsesBehalf() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        accept(signedInBrowser("andrei@atelier.ro"), task);

        ResponseEntity<String> refused = browser.post("/api/tasks/%s/start".formatted(task), "");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("NOT_THE_ASSIGNEE");
        assertThat(stateOf(task)).isEqualTo("ACCEPTED");
    }

    @Test
    void severalTasksMayBeUnderwayAtOnceAndEachAccruesItsOwnActivePhase() throws Exception {
        Company company = buildTheCompany();
        String first = createTaskFor(company.andrei());
        String second = createTaskFor(company.andrei());
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");

        start(andrei, first);
        start(andrei, second);

        assertThat(activePhaseCount(first)).isEqualTo(1L);
        assertThat(activePhaseCount(second))
                .as("the second start is permitted; a person may have several things underway")
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                        "select count(*) from task_phase_timer p join task t on t.id = p.task_id"
                                + " where t.assignee_user_id = ?::uuid and p.phase_kind = 'ACTIVE'"
                                + " and p.ended_at is null",
                        Long.class,
                        company.andrei()))
                .as("two overlapping actives, which is exactly why no total may be summed from them")
                .isEqualTo(2L);
    }

    @Test
    void blockingClosesTheAssigneesClockAndRecordsWhatTheWorkIsWaitingOn() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        start(andrei, task);

        ResponseEntity<String> blocked =
                andrei.post("/api/tasks/%s/block".formatted(task), "{\"reason\":\"%s\"}".formatted(REASON));

        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(blocked.getBody()).get("state").asText()).isEqualTo("BLOCKED");

        assertThat(jdbc.queryForObject(
                        "select count(*) from task_phase_timer where task_id = ?::uuid"
                                + " and phase_kind = 'ACTIVE' and ended_at is not null",
                        Long.class,
                        task))
                .as("the interval attributed to Andrei has stopped")
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                        "select phase_kind from task_phase_timer where task_id = ?::uuid and ended_at is null",
                        String.class,
                        task))
                .isEqualTo("BLOCKED");

        assertThat(jdbc.queryForObject(
                        "select reason from task_state_transition where task_id = ?::uuid and to_state = 'BLOCKED'",
                        String.class,
                        task))
                .as("the reason is what makes the block actionable, and it lives on the transition")
                .isEqualTo(REASON);
        assertThat(eventCount(task, "TASK_BLOCKED")).isEqualTo(1L);
    }

    @Test
    void blockingWithNoReasonIsRefused() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        start(andrei, task);

        ResponseEntity<String> refused = andrei.post("/api/tasks/%s/block".formatted(task), "{\"reason\":\"   \"}");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("REQUEST_INVALID");
        assertThat(stateOf(task)).as("a refused block changes nothing").isEqualTo("IN_PROGRESS");
    }

    @Test
    void blockingWorkThatWasNeverStartedIsRefused() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        accept(andrei, task);

        ResponseEntity<String> refused =
                andrei.post("/api/tasks/%s/block".formatted(task), "{\"reason\":\"%s\"}".formatted(REASON));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(refused.getBody())
                        .get("details")
                        .get(0)
                        .get("rule")
                        .asText())
                .isEqualTo("ACCEPTED");
    }

    @Test
    void aBlockedTaskIsStillOverdueWhileItsBlockedTimeIsStillExcluded() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        start(andrei, task);
        block(andrei, task);
        jdbc.update(
                "update task set deadline = ? where id = ?::uuid",
                OffsetDateTime.now().minusDays(1),
                task);

        JsonNode row =
                json.readTree(andrei.get("/api/tasks").getBody()).get("tasks").get(0);

        assertThat(row.get("state").asText()).isEqualTo("BLOCKED");
        assertThat(Instant.parse(row.get("deadline").asText()))
                .as("the work is still late for the business, and hiding that would be the opposite of useful")
                .isBefore(Instant.now());
        assertThat(row.get("openPhase").asText())
                .as("what is accruing is blocked time, which counts against nobody")
                .isEqualTo("BLOCKED");
    }

    @Test
    void unblockingOpensANewActiveRowAndLeavesTheClosedOneExactlyAsItWas() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        start(andrei, task);
        block(andrei, task);

        Map<String, Object> firstActive = jdbc.queryForMap(
                "select id::text as id, started_at, ended_at from task_phase_timer"
                        + " where task_id = ?::uuid and phase_kind = 'ACTIVE'",
                task);

        ResponseEntity<String> unblocked = andrei.post(
                "/api/tasks/%s/unblock".formatted(task), "{\"resolution\":\"They sent the figures this morning\"}");

        assertThat(unblocked.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(unblocked.getBody()).get("state").asText()).isEqualTo("IN_PROGRESS");

        List<Map<String, Object>> actives = jdbc.queryForList(
                "select id::text as id, started_at, ended_at from task_phase_timer"
                        + " where task_id = ?::uuid and phase_kind = 'ACTIVE' order by started_at",
                task);
        assertThat(actives)
                .as("two intervals of work, not one that was stretched over the block")
                .hasSize(2);
        assertThat(actives.get(0).get("id")).isEqualTo(firstActive.get("id"));
        assertThat(actives.get(0).get("ended_at"))
                .as("no row is ever edited after it closes")
                .isEqualTo(firstActive.get("ended_at"));
        assertThat(actives.get(1).get("ended_at"))
                .as("the new interval is open")
                .isNull();
        assertThat(actives.get(1).get("id")).isNotEqualTo(firstActive.get("id"));

        assertThat(jdbc.queryForObject(
                        "select reason from task_state_transition where task_id = ?::uuid"
                                + " and from_state = 'BLOCKED' and to_state = 'IN_PROGRESS'",
                        String.class,
                        task))
                .isEqualTo("They sent the figures this morning");
        assertThat(eventCount(task, "TASK_UNBLOCKED")).isEqualTo(1L);
    }

    @Test
    void blockedTimeIsExcludedFromWhatIsAttributedToTheAssigneeAcrossRepeatedBlocks() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        start(andrei, task);
        block(andrei, task);
        unblock(andrei, task);
        block(andrei, task);
        unblock(andrei, task);

        assertThat(jdbc.queryForObject(
                        "select count(*) from task_phase_timer where task_id = ?::uuid and phase_kind = 'BLOCKED'",
                        Long.class,
                        task))
                .as("each block writes its own row; nothing is averaged")
                .isEqualTo(2L);
        assertThat(activePhaseCount(task))
                .as("three intervals of work: before the first block, between them, and after the second")
                .isEqualTo(3L);
        assertThat(jdbc.queryForObject(
                        "select count(*) from task_phase_timer where task_id = ?::uuid"
                                + " and phase_kind = 'BLOCKED' and ended_at is null",
                        Long.class,
                        task))
                .as("both blocked intervals are closed facts")
                .isZero();
        assertThat(jdbc.queryForObject(
                        "select count(distinct phase_kind) from task_phase_timer where task_id = ?::uuid"
                                + " and phase_kind = 'ACTIVE' and ended_at is null",
                        Long.class,
                        task))
                .as("exactly one interval is accruing against Andrei at the end of it")
                .isEqualTo(1L);
    }

    @Test
    void unblockingWithNoResolutionNoteSucceeds() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        start(andrei, task);
        block(andrei, task);

        assertThat(andrei.post("/api/tasks/%s/unblock".formatted(task), "{}").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(stateOf(task)).isEqualTo("IN_PROGRESS");
        assertThat(jdbc.queryForObject(
                        "select reason from task_state_transition where task_id = ?::uuid"
                                + " and from_state = 'BLOCKED' and to_state = 'IN_PROGRESS'",
                        String.class,
                        task))
                .isNull();
    }

    @Test
    void unblockingSomethingThatWasNeverBlockedIsRefused() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        start(andrei, task);

        ResponseEntity<String> refused = andrei.post("/api/tasks/%s/unblock".formatted(task), "{}");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(refused.getBody())
                        .get("details")
                        .get(0)
                        .get("rule")
                        .asText())
                .isEqualTo("IN_PROGRESS");
    }

    @Test
    void completingStoresTheProofAndMovesTheClockToTheReviewer() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        start(andrei, task);

        ResponseEntity<String> completed = andrei.post(
                "/api/tasks/%s/complete".formatted(task),
                "{\"note\":\"%s\",\"externalLink\":\"https://drive.example.ro/q3-review\"}".formatted(NOTE));

        assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(completed.getBody()).get("state").asText()).isEqualTo("COMPLETED");

        assertThat(jdbc.queryForObject(
                        "select phase_kind from task_phase_timer where task_id = ?::uuid and ended_at is null",
                        String.class,
                        task))
                .as("review latency is the reviewer's, never added to how long Andrei took")
                .isEqualTo("REVIEW");
        assertThat(jdbc.queryForObject(
                        "select count(*) from task_phase_timer where task_id = ?::uuid"
                                + " and phase_kind = 'ACTIVE' and ended_at is null",
                        Long.class,
                        task))
                .as("nothing accrues against the assignee once they have submitted")
                .isZero();

        Map<String, Object> proof =
                jdbc.queryForMap("select note, external_link from task_completion_proof where task_id = ?::uuid", task);
        assertThat(proof.get("note")).isEqualTo(NOTE);
        assertThat(proof.get("external_link"))
                .as("stored as text; UC-12 extension 2b says the system never fetches it")
                .isEqualTo("https://drive.example.ro/q3-review");
        assertThat(eventCount(task, "TASK_COMPLETED")).isEqualTo(1L);
    }

    @Test
    void completingWithNoProofNoteIsRefusedAndTheTaskStaysUnderway() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        start(andrei, task);

        ResponseEntity<String> refused = andrei.post("/api/tasks/%s/complete".formatted(task), "{\"note\":\"   \"}");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("REQUEST_INVALID");
        assertThat(stateOf(task)).isEqualTo("IN_PROGRESS");
        assertThat(jdbc.queryForObject(
                        "select count(*) from task_completion_proof where task_id = ?::uuid", Long.class, task))
                .as("a completed task with no evidence is the state this criterion makes impossible")
                .isZero();
    }

    @Test
    void completingABlockedTaskIsRefused() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        start(andrei, task);
        block(andrei, task);

        ResponseEntity<String> refused =
                andrei.post("/api/tasks/%s/complete".formatted(task), "{\"note\":\"%s\"}".formatted(NOTE));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(refused.getBody())
                        .get("details")
                        .get(0)
                        .get("rule")
                        .asText())
                .isEqualTo("BLOCKED");
    }

    @Test
    void completingAfterTheDeadlineSucceedsAndIsRecordedAsLate() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        start(andrei, task);

        jdbc.update(
                "update task set deadline = ? where id = ?::uuid",
                OffsetDateTime.now().minusDays(2),
                task);

        assertThat(andrei.post("/api/tasks/%s/complete".formatted(task), "{\"note\":\"%s\"}".formatted(NOTE))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        Timestamp submitted = (Timestamp)
                jdbc.queryForMap("select submitted_at from task_completion_proof where task_id = ?::uuid", task)
                        .get("submitted_at");
        assertThat(submitted.toInstant())
                .as("submitted after the deadline, and the record says so rather than refusing it")
                .isAfter(jdbc.queryForObject("select deadline from task where id = ?::uuid", Instant.class, task));
    }

    @Test
    void noneOfTheFourTransitionsIsReachableWithoutTaskActOwn() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        jdbc.update("delete from auth_role_permission where role_name = 'EMPLOYEE'"
                + " and permission_name = 'TASK_ACT_OWN'");
        try {
            RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");

            assertThat(List.of(
                            refusalCodeOf(andrei, task, "start", ""),
                            refusalCodeOf(andrei, task, "block", "{\"reason\":\"%s\"}".formatted(REASON)),
                            refusalCodeOf(andrei, task, "unblock", "{}"),
                            refusalCodeOf(andrei, task, "complete", "{\"note\":\"%s\"}".formatted(NOTE)),
                            refusalCodeOf(andrei, task, "reject", "{\"reason\":\"%s\"}".formatted(REASON)),
                            refusalCodeOf(
                                    andrei,
                                    task,
                                    "deadline-proposals",
                                    "{\"proposedDeadline\":\"%s\",\"reason\":\"%s\"}"
                                            .formatted(tomorrow().plusSeconds(86_400), REASON))))
                    .as("six doors, one grant, and the same refusal at each")
                    .containsOnly("403 NOT_PERMITTED");
            assertThat(stateOf(task)).as("nothing moved").isEqualTo("CREATED");
        } finally {
            jdbc.update("insert into auth_role_permission (role_name, permission_name)"
                    + " values ('EMPLOYEE', 'TASK_ACT_OWN')");
        }
    }

    @Test
    void anAnonymousCallerCannotMoveAnything() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        RoundTripClient stranger = new RoundTripClient(rest);

        assertThat(stranger.post("/api/tasks/%s/start".formatted(task), "").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void movingATaskThatDoesNotExistIsRefused() throws Exception {
        buildTheCompany();

        ResponseEntity<String> refused = browser.post("/api/tasks/%s/start".formatted(UUID.randomUUID()), "");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("TASK_NOT_FOUND");
    }

    @Test
    void erasingSomebodyLeavesTheReasonAndTheProofTheyWroteExactlyAsTheyWereWritten() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        start(andrei, task);
        block(andrei, task);
        unblock(andrei, task);
        assertThat(andrei.post("/api/tasks/%s/complete".formatted(task), "{\"note\":\"%s\"}".formatted(NOTE))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        String membership = jdbc.queryForObject(
                "select id::text from workspace_membership where user_id = ?::uuid", String.class, company.andrei());
        assertThat(browser.post("/api/workspace/people/%s/deactivate".formatted(membership), "")
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(browser.post("/api/auth/reauthenticate", "{\"password\":\"%s\"}".formatted(PASSWORD))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(browser.post(
                                "/api/workspace/people/%s/erase".formatted(membership),
                                "{\"typedName\":\"Andrei Munteanu\"}")
                        .getStatusCode())
                .as("prose somebody wrote must not become a foreign key that refuses to let them leave")
                .isEqualTo(HttpStatus.OK);

        assertThat(jdbc.queryForObject(
                        "select reason from task_state_transition where task_id = ?::uuid and to_state = 'BLOCKED'",
                        String.class,
                        task))
                .as("what the work was waiting on is the business's record, not Andrei's identity")
                .isEqualTo(REASON);
        assertThat(jdbc.queryForObject(
                        "select note from task_completion_proof where task_id = ?::uuid", String.class, task))
                .as("the evidence survives; the person it identifies does not")
                .isEqualTo(NOTE);
    }

    @Test
    void oneTaskWalkedFromGivenToSubmitted() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");

        accept(andrei, task);
        assertThat(openPhaseCount(task)).isEqualTo(1L);
        assertThat(andrei.post("/api/tasks/%s/start".formatted(task), "").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(openPhaseCount(task)).isEqualTo(1L);
        block(andrei, task);
        assertThat(openPhaseCount(task)).isEqualTo(1L);
        unblock(andrei, task);
        assertThat(openPhaseCount(task)).isEqualTo(1L);
        assertThat(andrei.post("/api/tasks/%s/complete".formatted(task), "{\"note\":\"%s\"}".formatted(NOTE))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(jdbc.queryForObject(
                        "select count(*) from task_state_transition where task_id = ?::uuid", Long.class, task))
                .as("created, accepted, started, blocked, unblocked, completed")
                .isEqualTo(6L);
        assertThat(jdbc.queryForList(
                        "select action from task_event where task_id = ?::uuid order by occurred_at, action",
                        String.class,
                        task))
                .containsExactlyInAnyOrder(
                        "TASK_CREATED",
                        "TASK_ACCEPTED",
                        "TASK_STARTED",
                        "TASK_BLOCKED",
                        "TASK_UNBLOCKED",
                        "TASK_COMPLETED");
        assertThat(openPhaseCount(task))
                .as("one phase open at every moment, including the last")
                .isEqualTo(1L);
    }

    @Test
    void aReasonLongerThanTheBoundaryAllowsIsRefusedBeforeAnyRuleRuns() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        start(andrei, task);

        ResponseEntity<String> refused =
                andrei.post("/api/tasks/%s/block".formatted(task), "{\"reason\":\"%s\"}".formatted("x".repeat(2001)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("REQUEST_INVALID");
        assertThat(stateOf(task))
                .as("nothing is written, and the domain never saw it")
                .isEqualTo("IN_PROGRESS");
    }

    @Test
    void aProofNoteLongerThanTheBoundaryAllowsIsRefused() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        start(andrei, task);

        ResponseEntity<String> refused =
                andrei.post("/api/tasks/%s/complete".formatted(task), "{\"note\":\"%s\"}".formatted("x".repeat(4001)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbc.queryForObject(
                        "select count(*) from task_completion_proof where task_id = ?::uuid", Long.class, task))
                .isZero();
    }

    @Test
    void unblockingWithNoBodyAtAllSucceeds() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        start(andrei, task);
        block(andrei, task);

        assertThat(andrei.post("/api/tasks/%s/unblock".formatted(task), null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(stateOf(task)).isEqualTo("IN_PROGRESS");
    }

    @Test
    void aSecondProofForOneTaskReplacesTheFirstRatherThanCollidingWithIt() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        start(andrei, task);
        assertThat(andrei.post("/api/tasks/%s/complete".formatted(task), "{\"note\":\"%s\"}".formatted(NOTE))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        String firstId = jdbc.queryForObject(
                "select id::text from task_completion_proof where task_id = ?::uuid", String.class, task);

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> completionProofs.save(CompletionProof.of(
                        TaskId.of(UUID.fromString(task)),
                        "Redone after review, with the corrected figures.",
                        null,
                        Instant.now())));

        assertThat(jdbc.queryForObject(
                        "select count(*) from task_completion_proof where task_id = ?::uuid", Long.class, task))
                .as("one task has one proof, and the second submission is the proof")
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                        "select id::text from task_completion_proof where task_id = ?::uuid", String.class, task))
                .as("the row keeps its identity, so anything pointing at it still resolves")
                .isEqualTo(firstId);
        assertThat(jdbc.queryForObject(
                        "select note from task_completion_proof where task_id = ?::uuid", String.class, task))
                .isEqualTo("Redone after review, with the corrected figures.");
    }

    private String refusalCodeOf(RoundTripClient client, String task, String action, String body) throws Exception {
        ResponseEntity<String> refused = client.post("/api/tasks/%s/%s".formatted(task, action), body);
        return refused.getStatusCode().value() + " "
                + json.readTree(refused.getBody()).get("code").asText();
    }

    private void accept(RoundTripClient client, String task) {
        assertThat(client.post("/api/tasks/%s/accept".formatted(task), "").getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    private void start(RoundTripClient client, String task) {
        accept(client, task);
        assertThat(client.post("/api/tasks/%s/start".formatted(task), "").getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    private void block(RoundTripClient client, String task) {
        assertThat(client.post("/api/tasks/%s/block".formatted(task), "{\"reason\":\"%s\"}".formatted(REASON))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    private void unblock(RoundTripClient client, String task) {
        assertThat(client.post("/api/tasks/%s/unblock".formatted(task), "{}").getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    private String stateOf(String task) {
        return jdbc.queryForObject("select state from task where id = ?::uuid", String.class, task);
    }

    private Long openPhaseCount(String task) {
        return jdbc.queryForObject(
                "select count(*) from task_phase_timer where task_id = ?::uuid and ended_at is null", Long.class, task);
    }

    private Long activePhaseCount(String task) {
        return jdbc.queryForObject(
                "select count(*) from task_phase_timer where task_id = ?::uuid and phase_kind = 'ACTIVE'",
                Long.class,
                task);
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

    private Long eventCount(String task, String action) {
        return jdbc.queryForObject(
                "select count(*) from task_event where task_id = ?::uuid and action = ?", Long.class, task, action);
    }
}
