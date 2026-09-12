package com.flowops.task.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowops.task.domain.enums.PhaseKind;
import com.flowops.task.domain.enums.TaskAction;
import com.flowops.task.domain.enums.TaskPriority;
import com.flowops.task.domain.enums.TaskState;
import com.flowops.task.domain.exception.CannotReviewOwnWorkException;
import com.flowops.task.domain.exception.IllegalTransitionException;
import com.flowops.task.domain.exception.ReworkReasonRequiredException;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.PhaseTimer;
import com.flowops.task.domain.model.Task;
import com.flowops.task.domain.model.TaskMove;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("TASK-APPROVE-01")
@Tag("TASK-REJECT-IN-REVIEW-01")
@Tag("TASK-CLOSE-01")
class TaskReviewMoveTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-10T09:00:00Z");
    private static final Instant NOW = Instant.parse("2026-08-11T14:00:00Z");
    private static final PersonId MARIA = PersonId.of(UUID.randomUUID());
    private static final PersonId IONUT = PersonId.of(UUID.randomUUID());
    private static final PersonId ANDREI = PersonId.of(UUID.randomUUID());

    @Test
    void approvingClosesTheReviewPhaseAndOpensTheApprovalPhase() {
        Task completed = completed();

        TaskMove move = TaskMove.approved(completed, reviewPhase(completed), IONUT, NOW);

        assertThat(move.task().state()).isEqualTo(TaskState.APPROVED);
        assertThat(move.closedPhase().orElseThrow().kind()).isEqualTo(PhaseKind.REVIEW);
        assertThat(move.closedPhase().orElseThrow().endedAt()).isEqualTo(NOW);
        assertThat(move.openedPhase().orElseThrow().kind()).isEqualTo(PhaseKind.APPROVAL);
        assertThat(move.event().action()).isEqualTo(TaskAction.TASK_APPROVED);
    }

    @Test
    void theApprovalIsRecordedAgainstTheReviewerAndNotTheAssignee() {
        Task completed = completed();

        TaskMove move = TaskMove.approved(completed, reviewPhase(completed), IONUT, NOW);

        assertThat(move.transition().actor()).isEqualTo(IONUT);
        assertThat(move.event().actor()).isEqualTo(IONUT);
        assertThat(move.task().assignee()).contains(ANDREI);
    }

    @Test
    void nobodyApprovesTheirOwnWork() {
        Task mine = Task.given(
                        "Draft the review",
                        null,
                        IONUT,
                        MARIA,
                        CREATED_AT.plusSeconds(86_400),
                        TaskPriority.NORMAL,
                        CREATED_AT)
                .accepted()
                .started()
                .completed();

        assertThatThrownBy(() -> mine.approvedBy(IONUT)).isInstanceOf(CannotReviewOwnWorkException.class);
    }

    @Test
    void ownWorkIsRefusedAsOwnWorkEvenWhenTheStateWouldRefuseItAnyway() {
        Task mine = Task.given(
                "Draft the review",
                null,
                IONUT,
                MARIA,
                CREATED_AT.plusSeconds(86_400),
                TaskPriority.NORMAL,
                CREATED_AT);

        assertThatThrownBy(() -> mine.approvedBy(IONUT)).isInstanceOf(CannotReviewOwnWorkException.class);
    }

    @Test
    void approvingWorkThatWasNeverSubmittedIsRefusedAndNamesTheState() {
        Task underway = task().accepted().started();

        assertThatThrownBy(() -> underway.approvedBy(IONUT))
                .isInstanceOf(IllegalTransitionException.class)
                .satisfies(failure -> assertThat(((IllegalTransitionException) failure).from())
                        .isEqualTo(TaskState.IN_PROGRESS));
    }

    @Test
    void returningClosesTheReviewPhaseAndOpensANewActiveOne() {
        Task completed = completed();

        TaskMove move = TaskMove.returnedForRework(
                completed, reviewPhase(completed), IONUT, "The figures for March are missing.", NOW);

        assertThat(move.task().state()).isEqualTo(TaskState.IN_PROGRESS);
        assertThat(move.closedPhase().orElseThrow().kind()).isEqualTo(PhaseKind.REVIEW);
        assertThat(move.openedPhase().orElseThrow().kind()).isEqualTo(PhaseKind.ACTIVE);
        assertThat(move.event().action()).isEqualTo(TaskAction.TASK_RETURNED_FOR_REWORK);
    }

    @Test
    void returnedWorkLandsInProgressAndKeepsItsAssignee() {
        Task completed = completed();

        TaskMove move = TaskMove.returnedForRework(completed, reviewPhase(completed), IONUT, "Not yet.", NOW);

        assertThat(move.task().state()).isNotEqualTo(TaskState.CREATED);
        assertThat(move.task().state()).isEqualTo(TaskState.IN_PROGRESS);
        assertThat(move.task().assignee()).contains(ANDREI);
        assertThat(move.task().deadline()).isEqualTo(completed.deadline());
    }

    @Test
    void theActivePhaseOpenedIsANewRowRatherThanTheClosedOneResumed() {
        Task completed = completed();
        PhaseTimer review = reviewPhase(completed);

        TaskMove move = TaskMove.returnedForRework(completed, review, IONUT, "Not yet.", NOW);

        assertThat(move.openedPhase().orElseThrow().id()).isNotEqualTo(review.id());
        assertThat(move.openedPhase().orElseThrow().startedAt()).isEqualTo(NOW);
        assertThat(move.openedPhase().orElseThrow().endedAt()).isNull();
    }

    @Test
    void theReasonForReturningIsRecordedOnTheTransition() {
        Task completed = completed();

        TaskMove move = TaskMove.returnedForRework(
                completed, reviewPhase(completed), IONUT, "The figures for March are missing.", NOW);

        assertThat(move.transition().statedReason()).contains("The figures for March are missing.");
    }

    @Test
    void returningWithNoReasonIsRefused() {
        Task completed = completed();

        assertThatThrownBy(() -> TaskMove.returnedForRework(completed, reviewPhase(completed), IONUT, "   ", NOW))
                .isInstanceOf(ReworkReasonRequiredException.class);
    }

    @Test
    void nobodyReturnsTheirOwnWorkEither() {
        Task mine = Task.given(
                        "Draft the review",
                        null,
                        IONUT,
                        MARIA,
                        CREATED_AT.plusSeconds(86_400),
                        TaskPriority.NORMAL,
                        CREATED_AT)
                .accepted()
                .started()
                .completed();

        assertThatThrownBy(() -> mine.returnedForReworkBy(IONUT)).isInstanceOf(CannotReviewOwnWorkException.class);
    }

    @Test
    void closingClosesTheApprovalPhaseAndOpensNothingAtAll() {
        Task approved = completed().approvedBy(IONUT);

        TaskMove move = TaskMove.closed(approved, approvalPhase(approved), IONUT, NOW);

        assertThat(move.task().state()).isEqualTo(TaskState.CLOSED);
        assertThat(move.closedPhase().orElseThrow().kind()).isEqualTo(PhaseKind.APPROVAL);
        assertThat(move.openedPhase()).isEmpty();
        assertThat(move.event().action()).isEqualTo(TaskAction.TASK_CLOSED);
    }

    @Test
    void closingWorkThatWasNeverApprovedIsRefusedAndNamesTheState() {
        Task completed = completed();

        assertThatThrownBy(completed::closed)
                .isInstanceOf(IllegalTransitionException.class)
                .satisfies(failure -> assertThat(((IllegalTransitionException) failure).from())
                        .isEqualTo(TaskState.COMPLETED));
    }

    @Test
    void closingATaskThatIsAlreadyClosedIsRefused() {
        Task closed = completed().approvedBy(IONUT).closed();

        assertThatThrownBy(closed::closed)
                .isInstanceOf(IllegalTransitionException.class)
                .satisfies(failure -> assertThat(((IllegalTransitionException) failure).from())
                        .isEqualTo(TaskState.CLOSED));
    }

    @Test
    void aClosedTaskOccupiesNoPhase() {
        assertThat(TaskState.CLOSED.openPhase()).isEmpty();
    }

    private static Task task() {
        return Task.given(
                "Draft the supplier review",
                null,
                ANDREI,
                MARIA,
                CREATED_AT.plusSeconds(86_400),
                TaskPriority.NORMAL,
                CREATED_AT);
    }

    private static Task completed() {
        return task().accepted().started().completed();
    }

    private static PhaseTimer reviewPhase(Task task) {
        return PhaseTimer.opened(task.id(), PhaseKind.REVIEW, CREATED_AT);
    }

    private static PhaseTimer approvalPhase(Task task) {
        return PhaseTimer.opened(task.id(), PhaseKind.APPROVAL, CREATED_AT);
    }
}
