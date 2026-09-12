package com.flowops.task.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.flowops.support.RoundTripClient;
import com.flowops.task.application.shared.port.AppendTaskEventPort;
import com.flowops.task.application.shared.port.SaveApprovalPort;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@Tag("TASK-APPROVE-01")
@Tag("TASK-REJECT-IN-REVIEW-01")
class TaskReviewAtomicityTest extends TaskScenarioTest {
    private static final String NOTE = "Compared both quarters and sent the summary to the board.";

    @MockitoSpyBean
    private SaveApprovalPort approvals;

    @MockitoSpyBean
    private AppendTaskEventPort taskEvents;

    @Test
    void whenTheJudgementCannotBeStoredTheWorkIsStillWaitingToBeJudged() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        complete(task);

        doThrow(new IllegalStateException("the approval store is unreachable"))
                .when(approvals)
                .save(any());

        ResponseEntity<String> attempted = browser.post("/api/tasks/%s/approve".formatted(task), "{\"score\":4}");

        assertThat(attempted.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        assertThat(jdbc.queryForObject("select state from task where id = ?::uuid", String.class, task))
                .as("a task approved by nobody would be finished with no judgement anybody could read")
                .isEqualTo("COMPLETED");
        assertThat(count("select count(*) from task_approval where task_id = ?::uuid", task))
                .isZero();
        assertThat(count(
                        "select count(*) from task_phase_timer where task_id = ?::uuid and phase_kind = 'APPROVAL'",
                        task))
                .as("an approval phase accruing against a reviewer who never approved anything")
                .isZero();
        assertThat(count(
                        "select count(*) from task_phase_timer where task_id = ?::uuid and phase_kind = 'REVIEW'"
                                + " and ended_at is null",
                        task))
                .as("the review phase must still be running, because the review did not happen")
                .isEqualTo(1L);
        assertThat(count(
                        "select count(*) from task_state_transition where task_id = ?::uuid and to_state = 'APPROVED'",
                        task))
                .isZero();
        assertThat(count("select count(*) from task_event where task_id = ?::uuid and action = 'TASK_APPROVED'", task))
                .as("an event asserting a transition that did not happen is a false row in a log that cannot be"
                        + " rewritten")
                .isZero();
    }

    @Test
    void whenTheEventCannotBeAppendedTheWorkDoesNotQuietlyGoBackToTheAssignee() throws Exception {
        Company company = buildTheCompany();
        String task = createTaskFor(company.andrei());
        complete(task);

        doThrow(new IllegalStateException("the event log is unreachable"))
                .when(taskEvents)
                .append(any());

        ResponseEntity<String> attempted =
                browser.post("/api/tasks/%s/return".formatted(task), "{\"reason\":\"The March figures are missing.\"}");

        assertThat(attempted.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        assertThat(jdbc.queryForObject("select state from task where id = ?::uuid", String.class, task))
                .as("the assignee would otherwise find work back in their queue with nothing on the record saying"
                        + " why")
                .isEqualTo("COMPLETED");
        assertThat(count(
                        "select count(*) from task_phase_timer where task_id = ?::uuid and phase_kind = 'ACTIVE'"
                                + " and ended_at is null",
                        task))
                .as("a clock running against somebody who was never told to start again")
                .isZero();
        assertThat(count(
                        "select count(*) from task_state_transition where task_id = ?::uuid and from_state ="
                                + " 'COMPLETED' and to_state = 'IN_PROGRESS'",
                        task))
                .isZero();
    }

    private Long count(String sql, String task) {
        return jdbc.queryForObject(sql, Long.class, task);
    }

    private void complete(String task) {
        RoundTripClient andrei = signedInBrowser("andrei@atelier.ro");
        assertThat(andrei.post("/api/tasks/%s/accept".formatted(task), "").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(andrei.post("/api/tasks/%s/start".formatted(task), "").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(andrei.post("/api/tasks/%s/complete".formatted(task), "{\"note\":\"%s\"}".formatted(NOTE))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }
}
