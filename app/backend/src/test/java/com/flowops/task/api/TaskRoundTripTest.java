package com.flowops.task.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowops.support.RoundTripClient;
import com.flowops.task.domain.enums.PhaseKind;
import com.flowops.task.domain.model.PhaseTimer;
import com.flowops.task.domain.model.PhaseTimerId;
import com.flowops.task.domain.model.TaskId;
import com.flowops.task.infrastructure.persistence.PhaseTimerPersistenceAdapter;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("TASK-CREATE-01")
class TaskRoundTripTest extends TaskScenarioTest {
    @Autowired
    private PhaseTimerPersistenceAdapter phaseTimers;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void anAnonymousCallerCannotCreateWork() {
        ResponseEntity<String> refused = browser.post("/api/tasks", body(UUID.randomUUID(), tomorrow()));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void theOwnerGivesWorkAndItsWaitPhaseOpensWithIt() throws Exception {
        Company company = buildTheCompany();

        ResponseEntity<String> created = browser.post("/api/tasks", body(company.andrei(), tomorrow()));

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode task = json.readTree(created.getBody());
        assertThat(task.get("state").asText()).isEqualTo("CREATED");
        assertThat(task.get("assigneeId").asText()).isEqualTo(company.andrei().toString());
        assertThat(task.get("assigneeName").asText()).isEqualTo("Andrei Munteanu");
        assertThat(task.get("selfAssigned").asBoolean()).isFalse();

        String id = task.get("id").asText();
        Map<String, Object> phase =
                jdbc.queryForMap("select phase_kind, ended_at from task_phase_timer where task_id = ?::uuid", id);
        assertThat(phase.get("phase_kind")).isEqualTo("WAIT");
        assertThat(phase.get("ended_at")).as("the wait phase is open").isNull();

        Map<String, Object> transition = jdbc.queryForMap(
                "select from_state, to_state, actor_user_id::text as actor"
                        + " from task_state_transition where task_id = ?::uuid",
                id);
        assertThat(transition.get("from_state"))
                .as("creation comes from nowhere")
                .isNull();
        assertThat(transition.get("to_state")).isEqualTo("CREATED");
        assertThat(transition.get("actor")).isEqualTo(company.maria().toString());

        assertThat(jdbc.queryForObject("select action from task_event where task_id = ?::uuid", String.class, id))
                .isEqualTo("TASK_CREATED");
    }

    @Test
    void aTaskStoresIdentifiersAndNeverAName() throws Exception {
        Company company = buildTheCompany();
        browser.post("/api/tasks", body(company.andrei(), tomorrow()));

        assertThat(jdbc.queryForList(
                        "select column_name from information_schema.columns"
                                + " where table_name = 'task' and data_type in ('text', 'character varying')",
                        String.class))
                .as("no column on a task may hold what somebody is called")
                .containsExactlyInAnyOrder("title", "description", "priority", "state", "kind");
    }

    @Test
    void aDeadlineInThePastIsRefused() throws Exception {
        Company company = buildTheCompany();

        ResponseEntity<String> refused =
                browser.post("/api/tasks", body(company.andrei(), Instant.now().minusSeconds(3_600)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("DEADLINE_IN_THE_PAST");
        assertThat(jdbc.queryForObject("select count(*) from task", Long.class)).isZero();
    }

    @Test
    void aTaskWithNoDeadlineIsCreatedUndatedRatherThanRefused() throws Exception {
        Company company = buildTheCompany();

        ResponseEntity<String> created = browser.post(
                "/api/tasks",
                "{\"title\":\"Draft the supplier review\",\"assigneeId\":\"%s\",\"priority\":\"NORMAL\"}"
                        .formatted(company.andrei()));

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(json.readTree(created.getBody()).get("deadline").isNull())
                .as("nobody was made to guess a date")
                .isTrue();
        assertThat(jdbc.queryForObject("select deadline from task limit 1", java.sql.Timestamp.class))
                .isNull();
    }

    @Test
    void aTaskWithNoPriorityIsCreatedAsNormal() throws Exception {
        Company company = buildTheCompany();

        ResponseEntity<String> created = browser.post(
                "/api/tasks",
                "{\"title\":\"Draft the supplier review\",\"assigneeId\":\"%s\"}".formatted(company.andrei()));

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(json.readTree(created.getBody()).get("priority").asText()).isEqualTo("NORMAL");
    }

    @Test
    void aTaskWithNoTitleIsRefused() throws Exception {
        Company company = buildTheCompany();

        ResponseEntity<String> refused = browser.post(
                "/api/tasks",
                "{\"title\":\"   \",\"assigneeId\":\"%s\",\"deadline\":\"%s\",\"priority\":\"NORMAL\"}"
                        .formatted(company.andrei(), tomorrow()));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("REQUEST_INVALID");
    }

    @Test
    void workCannotBeGivenToSomebodyWhoseAccessHasEnded() throws Exception {
        Company company = buildTheCompany();
        jdbc.update(
                "update workspace_membership set status = 'DEACTIVATED', deactivated_at = ?"
                        + " where user_id = ?::uuid",
                OffsetDateTime.now(),
                company.elena());

        ResponseEntity<String> refused = browser.post("/api/tasks", body(company.elena(), tomorrow()));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("ASSIGNEE_NOT_ACTIVE");
    }

    @Test
    void aManagerMayDirectTheirOwnSubtreeAndNobodyElse() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient ionut = signedInBrowser("ionut@atelier.ro");

        assertThat(ionut.post("/api/tasks", body(company.andrei(), tomorrow())).getStatusCode())
                .as("Andrei is two levels below Ionuț and the subtree travels down, not one hop")
                .isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> refused = ionut.post("/api/tasks", body(company.elena(), tomorrow()));
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("ASSIGNEE_OUT_OF_SCOPE");
    }

    @Test
    void givingYourselfWorkSucceedsAndIsFlagged() throws Exception {
        Company company = buildTheCompany();

        ResponseEntity<String> created = browser.post("/api/tasks", body(company.maria(), tomorrow()));

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(json.readTree(created.getBody()).get("selfAssigned").asBoolean())
                .isTrue();
        assertThat(jdbc.queryForObject("select self_assigned from task", Boolean.class))
                .as("TASK-APPROVE-01 reads this later, so it is stored rather than derived")
                .isTrue();
    }

    @Test
    void anEmployeeMayCreateWorkForThemselves() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");

        ResponseEntity<String> created = andrei.post("/api/tasks", body(company.andrei(), tomorrow()));

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(json.readTree(created.getBody()).get("selfAssigned").asBoolean())
                .as("self-assignment is flagged whoever does it — TASK-APPROVE-01 reads it later")
                .isTrue();
    }

    @Test
    void anEmployeeMayNotGiveWorkToAPeer() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");

        ResponseEntity<String> refused = andrei.post("/api/tasks", body(company.elena(), tomorrow()));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("ASSIGNEE_OUT_OF_SCOPE");
    }

    @Test
    void anEmployeeMayDateTheTaskTheyCreatedForThemselves() throws Exception {
        Company company = buildTheCompany();
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        String task = json.readTree(andrei.post("/api/tasks", body(company.andrei(), tomorrow()))
                        .getBody())
                .get("id")
                .asText();

        ResponseEntity<String> edited = andrei.exchange(
                HttpMethod.PUT,
                "/api/tasks/" + task,
                "{\"deadline\":\"%s\",\"priority\":\"HIGH\"}".formatted(tomorrow()));

        assertThat(edited.getStatusCode())
                .as("the creator's change affordance is offered unconditionally, so it must not answer 403")
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void aCallerWithoutTaskCreateIsRefused() throws Exception {
        Company company = buildTheCompany();
        jdbc.update("delete from auth_role_permission where role_name = 'OWNER' and permission_name = 'TASK_CREATE'");
        try {
            browser.forget();
            signIn(browser, OWNER_EMAIL);

            ResponseEntity<String> refused = browser.post("/api/tasks", body(company.andrei(), tomorrow()));

            assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("NOT_PERMITTED");
        } finally {
            jdbc.update("insert into auth_role_permission (role_name, permission_name)"
                    + " values ('OWNER', 'TASK_CREATE')");
        }
    }

    @Test
    void aPhaseThatHasAlreadyClosedCannotBeClosedAgain() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        assertThat(signedInBrowser("andrei@atelier.ro")
                        .post("/api/tasks/%s/accept".formatted(task), "")
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> closedRow = jdbc.queryForMap(
                "select id::text as id, started_at, ended_at from task_phase_timer"
                        + " where task_id = ?::uuid and ended_at is not null",
                task);
        PhaseTimer alreadyClosed = new PhaseTimer(
                PhaseTimerId.of(UUID.fromString((String) closedRow.get("id"))),
                TaskId.of(UUID.fromString(task)),
                PhaseKind.WAIT,
                ((java.sql.Timestamp) closedRow.get("started_at")).toInstant(),
                Instant.now());

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager)
                        .executeWithoutResult(status -> phaseTimers.close(alreadyClosed)))
                .as("closing nothing must fail loudly rather than leave a clock running")
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void erasingSomebodyWhoHoldsWorkSucceedsAndLeavesTheirWorkReadingAsAFormerMembers() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        String andreiMembership = jdbc.queryForObject(
                "select id::text from workspace_membership where user_id = ?::uuid", String.class, company.andrei());

        assertThat(browser.post("/api/workspace/people/%s/deactivate".formatted(andreiMembership), "")
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(browser.post("/api/auth/reauthenticate", "{\"password\":\"%s\"}".formatted(PASSWORD))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<String> erased = browser.post(
                "/api/workspace/people/%s/erase".formatted(andreiMembership), "{\"typedName\":\"Andrei Munteanu\"}");

        assertThat(erased.getStatusCode())
                .as("a task must not become a foreign key that refuses to let somebody leave")
                .isEqualTo(HttpStatus.OK);

        assertThat(jdbc.queryForObject(
                        "select assignee_user_id::text from task where id = ?::uuid", String.class, task))
                .as("the work happened; erasure destroys who they were, not what was done")
                .isEqualTo(company.andrei().toString());

        JsonNode row =
                json.readTree(browser.get("/api/tasks").getBody()).get("tasks").get(0);
        assertThat(row.get("assigneeName").asText())
                .as("no name resolves, so the screen renders a former member — with nothing rewritten")
                .isEmpty();
    }

    @Test
    void thePickerOffersTheCallersOwnSubtreeAndThemselves() throws Exception {
        Company company = buildTheCompany();

        JsonNode offered = json.readTree(signedInBrowser("ionut@atelier.ro")
                        .get("/api/tasks/assignable-people")
                        .getBody())
                .get("people");

        assertThat(identifiersIn(offered))
                .as("his subtree and himself, and Elena reports to Maria")
                .containsExactlyInAnyOrder(
                        company.ionut().toString(),
                        company.ioana().toString(),
                        company.andrei().toString());
    }

    @Test
    void thePickerOffersTheWholeCompanyToTheOwner() throws Exception {
        Company company = buildTheCompany();

        JsonNode offered = json.readTree(
                        browser.get("/api/tasks/assignable-people").getBody())
                .get("people");

        assertThat(identifiersIn(offered))
                .containsExactlyInAnyOrder(
                        company.maria().toString(),
                        company.ionut().toString(),
                        company.ioana().toString(),
                        company.andrei().toString(),
                        company.elena().toString());
    }

    @Test
    void thePickerLeavesOutSomebodyWhoseAccessHasEnded() throws Exception {
        Company company = buildTheCompany();
        jdbc.update(
                "update workspace_membership set status = 'DEACTIVATED', deactivated_at = ?"
                        + " where user_id = ?::uuid",
                OffsetDateTime.now(),
                company.elena());

        JsonNode offered = json.readTree(
                        browser.get("/api/tasks/assignable-people").getBody())
                .get("people");

        assertThat(identifiersIn(offered)).doesNotContain(company.elena().toString());
        assertThat(identifiersIn(offered)).as("and still offers everybody else").hasSize(4);
    }

    @Test
    void anEmployeeIsOfferedThemselvesAndNobodyElse() throws Exception {
        Company company = buildTheCompany();

        ResponseEntity<String> offered = signedInBrowser("andrei@atelier.ro").get("/api/tasks/assignable-people");

        assertThat(offered.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(identifiersIn(json.readTree(offered.getBody()).get("people")))
                .as("an employee may create for themselves and for nobody else")
                .containsExactly(company.andrei().toString());
    }

    private static java.util.List<String> identifiersIn(JsonNode people) {
        java.util.List<String> identifiers = new java.util.ArrayList<>();
        people.forEach(person -> identifiers.add(person.get("id").asText()));
        return identifiers;
    }

    @Test
    void theAssigneeAcceptsAndTheWaitPhaseClosesWithNothingCountingAgainstThem() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");

        ResponseEntity<String> accepted = andrei.post("/api/tasks/%s/accept".formatted(task), "");

        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(accepted.getBody()).get("state").asText()).isEqualTo("ACCEPTED");

        assertThat(jdbc.queryForObject("select state from task where id = ?::uuid", String.class, task))
                .isEqualTo("ACCEPTED");
        assertThat(jdbc.queryForObject(
                        "select count(*) from task_phase_timer where task_id = ?::uuid and ended_at is not null",
                        Long.class,
                        task))
                .as("the wait phase the task was created in is closed")
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                        "select count(*) from task_phase_timer where task_id = ?::uuid and ended_at is null",
                        Long.class,
                        task))
                .as("exactly one phase is open at any moment")
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                        "select count(*) from task_phase_timer where task_id = ?::uuid and phase_kind = 'ACTIVE'",
                        Long.class,
                        task))
                .as("accepted is not started; nothing may accrue against them yet")
                .isZero();

        assertThat(jdbc.queryForObject(
                        "select count(*) from task_state_transition"
                                + " where task_id = ?::uuid and from_state = 'CREATED' and to_state = 'ACCEPTED'",
                        Long.class,
                        task))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                        "select count(*) from task_event where task_id = ?::uuid and action = 'TASK_ACCEPTED'",
                        Long.class,
                        task))
                .isEqualTo(1L);
    }

    @Test
    void aManagerCannotAcceptOnSomebodyElsesBehalf() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());

        ResponseEntity<String> refused = browser.post("/api/tasks/%s/accept".formatted(task), "");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("NOT_THE_ASSIGNEE");
        assertThat(jdbc.queryForObject("select state from task where id = ?::uuid", String.class, task))
                .as("a refused acceptance changes nothing")
                .isEqualTo("CREATED");
    }

    @Test
    void acceptingTwiceIsRefusedAndNamesTheStateTheTaskIsIn() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        assertThat(andrei.post("/api/tasks/%s/accept".formatted(task), "").getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<String> refused = andrei.post("/api/tasks/%s/accept".formatted(task), "");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode body = json.readTree(refused.getBody());
        assertThat(body.get("code").asText()).isEqualTo("ILLEGAL_TRANSITION");
        assertThat(body.get("details").get(0).get("rule").asText()).isEqualTo("ACCEPTED");
        assertThat(jdbc.queryForObject(
                        "select count(*) from task_event where task_id = ?::uuid and action = 'TASK_ACCEPTED'",
                        Long.class,
                        task))
                .as("one acknowledgement happened, so there is one event")
                .isEqualTo(1L);
    }

    @Test
    void acceptingAfterTheDeadlineHasPassedStillSucceeds() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());

        jdbc.update(
                "update task set deadline = ? where id = ?::uuid",
                OffsetDateTime.now().minusDays(1),
                task);

        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");

        assertThat(andrei.post("/api/tasks/%s/accept".formatted(task), "").getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void aCallerWithoutTaskActOwnCannotAcceptEvenTheirOwnWork() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        jdbc.update("delete from auth_role_permission where role_name = 'EMPLOYEE'"
                + " and permission_name = 'TASK_ACT_OWN'");
        try {
            ResponseEntity<String> refused =
                    signedInBrowser("andrei@atelier.ro").post("/api/tasks/%s/accept".formatted(task), "");

            assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("NOT_PERMITTED");
            assertThat(jdbc.queryForObject("select state from task where id = ?::uuid", String.class, task))
                    .as("a refused acceptance changes nothing")
                    .isEqualTo("CREATED");
        } finally {
            jdbc.update("insert into auth_role_permission (role_name, permission_name)"
                    + " values ('EMPLOYEE', 'TASK_ACT_OWN')");
        }
    }

    @Test
    void acceptingATaskThatDoesNotExistIsRefused() throws Exception {
        buildTheCompany();

        ResponseEntity<String> refused = browser.post("/api/tasks/%s/accept".formatted(UUID.randomUUID()), "");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(json.readTree(refused.getBody()).get("code").asText()).isEqualTo("TASK_NOT_FOUND");
    }

    @Test
    void whatComesBackIsScopedByWhatTheCallerHolds() throws Exception {
        Company company = buildTheCompany();
        createTaskFor(company.andrei());
        createTaskFor(company.elena());

        assertThat(json.readTree(signedInBrowser("andrei@atelier.ro")
                                .get("/api/tasks")
                                .getBody())
                        .get("tasks"))
                .as("an employee sees the work they were given and nothing else")
                .hasSize(1);

        JsonNode ionutSees = json.readTree(
                        signedInBrowser("ionut@atelier.ro").get("/api/tasks").getBody())
                .get("tasks");
        assertThat(ionutSees).as("Andrei is in his subtree; Elena is not").hasSize(1);
        assertThat(ionutSees.get(0).get("assigneeId").asText())
                .isEqualTo(company.andrei().toString());
        assertThat(ionutSees.get(0).get("mine").asBoolean())
                .as("a manager looking at somebody else's work is not offered an action on it")
                .isFalse();

        assertThat(json.readTree(browser.get("/api/tasks").getBody()).get("tasks"))
                .as("the owner holds TASK_VIEW_ANY")
                .hasSize(2);
    }

    @Test
    void everyRowCarriesThePhaseBesideTheInstantItBegan() throws Exception {
        Company company = buildTheCompany();
        createTaskFor(company.andrei());

        JsonNode row =
                json.readTree(browser.get("/api/tasks").getBody()).get("tasks").get(0);

        assertThat(row.get("openPhase").asText()).isEqualTo("WAIT");
        assertThat(row.get("phaseSince").isNull()).isFalse();
        assertThat(row.get("assigneeName").asText()).isEqualTo("Andrei Munteanu");
    }

    @Test
    void theQueueComesBackNewestFirst() throws Exception {
        Company company = buildTheCompany();
        String older = createTaskFor(company.andrei());

        jdbc.update("update task set created_at = created_at - interval '1 hour' where id = ?::uuid", older);
        String newer = createTaskFor(company.elena());

        JsonNode tasks = json.readTree(browser.get("/api/tasks").getBody()).get("tasks");

        assertThat(tasks).hasSize(2);
        assertThat(tasks.get(0).get("id").asText()).isEqualTo(newer);
        assertThat(tasks.get(1).get("id").asText()).isEqualTo(older);
    }

    @Test
    void aCallerWithoutTaskViewOwnIsRefused() throws Exception {
        buildTheCompany();
        jdbc.update("delete from auth_role_permission where role_name = 'OWNER' and permission_name = 'TASK_VIEW_OWN'");
        try {
            browser.forget();
            signIn(browser, OWNER_EMAIL);

            ResponseEntity<String> refused = browser.get("/api/tasks");

            assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        } finally {
            jdbc.update("insert into auth_role_permission (role_name, permission_name)"
                    + " values ('OWNER', 'TASK_VIEW_OWN')");
        }
    }
}
