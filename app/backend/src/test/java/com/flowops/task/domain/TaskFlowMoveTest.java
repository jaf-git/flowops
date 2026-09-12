package com.flowops.task.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowops.task.domain.enums.PhaseKind;
import com.flowops.task.domain.enums.TaskAction;
import com.flowops.task.domain.enums.TaskPriority;
import com.flowops.task.domain.enums.TaskState;
import com.flowops.task.domain.exception.BlockReasonRequiredException;
import com.flowops.task.domain.exception.IllegalTransitionException;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.PhaseTimer;
import com.flowops.task.domain.model.Task;
import com.flowops.task.domain.model.TaskMove;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("TASK-START-01")
@Tag("TASK-BLOCK-01")
@Tag("TASK-UNBLOCK-01")
@Tag("TASK-COMPLETE-01")
class TaskFlowMoveTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-11T09:00:00Z");
    private static final Instant NOW = Instant.parse("2026-08-11T14:00:00Z");
    private static final PersonId MARIA = PersonId.of(UUID.randomUUID());
    private static final PersonId ANDREI = PersonId.of(UUID.randomUUID());

    @Test
    void startingClosesTheWaitPhaseAndOpensTheOneThatCountsAgainstTheAssignee() {
        Task accepted = newTask().accepted();
        PhaseTimer waiting = PhaseTimer.opened(accepted.id(), PhaseKind.WAIT, CREATED_AT);

        TaskMove move = TaskMove.started(accepted, waiting, NOW);

        assertThat(move.task().state()).isEqualTo(TaskState.IN_PROGRESS);
        assertThat(move.closedPhase().orElseThrow().kind()).isEqualTo(PhaseKind.WAIT);
        assertThat(move.closedPhase().orElseThrow().endedAt()).isEqualTo(NOW);
        assertThat(move.openedPhase().orElseThrow().kind()).isEqualTo(PhaseKind.ACTIVE);
        assertThat(move.openedPhase().orElseThrow().countsAgainstTheAssignee())
                .as("this is the only interval in the product attributed to a person")
                .isTrue();
        assertThat(move.event().action()).isEqualTo(TaskAction.TASK_STARTED);
        assertThat(move.transition().cameFrom()).contains(TaskState.ACCEPTED);
    }

    @Test
    void startingWorkThatWasNeverAcknowledgedIsRefusedAndNamesTheState() {
        Task created = newTask();

        assertThatThrownBy(() ->
                        TaskMove.started(created, PhaseTimer.opened(created.id(), PhaseKind.WAIT, CREATED_AT), NOW))
                .isInstanceOf(IllegalTransitionException.class)
                .satisfies(failure -> assertThat(((IllegalTransitionException) failure).from())
                        .isEqualTo(TaskState.CREATED));
    }

    @Test
    void startingATaskThatIsAlreadyUnderwayIsRefused() {
        Task underway = newTask().accepted().started();

        assertThatThrownBy(() ->
                        TaskMove.started(underway, PhaseTimer.opened(underway.id(), PhaseKind.ACTIVE, CREATED_AT), NOW))
                .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void blockingStopsTheAssigneesClockAndRecordsWhatTheWorkIsWaitingOn() {
        Task underway = newTask().accepted().started();
        PhaseTimer active = PhaseTimer.opened(underway.id(), PhaseKind.ACTIVE, CREATED_AT);

        TaskMove move = TaskMove.blocked(underway, active, "The supplier has not sent last quarter's figures", NOW);

        assertThat(move.task().state()).isEqualTo(TaskState.BLOCKED);
        assertThat(move.closedPhase().orElseThrow().kind()).isEqualTo(PhaseKind.ACTIVE);
        assertThat(move.openedPhase().orElseThrow().kind()).isEqualTo(PhaseKind.BLOCKED);
        assertThat(move.openedPhase().orElseThrow().countsAgainstTheAssignee())
                .as("a person waiting on a supplier has not been slow")
                .isFalse();
        assertThat(move.transition().statedReason()).contains("The supplier has not sent last quarter's figures");
        assertThat(move.event().action()).isEqualTo(TaskAction.TASK_BLOCKED);
    }

    @Test
    void blockingWithNothingSaidAboutWhyIsRefused() {
        Task underway = newTask().accepted().started();
        PhaseTimer active = PhaseTimer.opened(underway.id(), PhaseKind.ACTIVE, CREATED_AT);

        assertThatThrownBy(() -> TaskMove.blocked(underway, active, null, NOW))
                .isInstanceOf(BlockReasonRequiredException.class);
        assertThatThrownBy(() -> TaskMove.blocked(underway, active, "   ", NOW))
                .as("a reason of spaces is no reason at all")
                .isInstanceOf(BlockReasonRequiredException.class);
    }

    @Test
    void blockingWorkThatWasNeverStartedIsRefused() {
        Task accepted = newTask().accepted();

        assertThatThrownBy(() -> TaskMove.blocked(
                        accepted,
                        PhaseTimer.opened(accepted.id(), PhaseKind.WAIT, CREATED_AT),
                        "Waiting on legal",
                        NOW))
                .isInstanceOf(IllegalTransitionException.class)
                .satisfies(failure -> assertThat(((IllegalTransitionException) failure).from())
                        .isEqualTo(TaskState.ACCEPTED));
    }

    @Test
    void unblockingOpensANewActiveRowRatherThanReopeningTheClosedOne() {
        Task underway = newTask().accepted().started();
        PhaseTimer firstActive = PhaseTimer.opened(underway.id(), PhaseKind.ACTIVE, CREATED_AT);
        TaskMove blocked = TaskMove.blocked(underway, firstActive, "The supplier has not replied", NOW);
        PhaseTimer blockedPhase = blocked.openedPhase().orElseThrow();

        TaskMove move =
                TaskMove.unblocked(blocked.task(), blockedPhase, "They sent the figures", NOW.plusSeconds(3600));

        assertThat(move.task().state()).isEqualTo(TaskState.IN_PROGRESS);
        assertThat(move.closedPhase().orElseThrow().kind()).isEqualTo(PhaseKind.BLOCKED);
        assertThat(move.openedPhase().orElseThrow().kind()).isEqualTo(PhaseKind.ACTIVE);
        assertThat(move.openedPhase().orElseThrow().id())
                .as("total active time is the sum of the rows, so this must be a second row")
                .isNotEqualTo(firstActive.id());
        assertThat(move.openedPhase().orElseThrow().startedAt())
                .as("the clock resumes when the person resumed, not when the blocker technically cleared")
                .isEqualTo(NOW.plusSeconds(3600));
        assertThat(move.event().action()).isEqualTo(TaskAction.TASK_UNBLOCKED);
    }

    @Test
    void unblockingWithNoResolutionNoteSucceedsAndRecordsNoReason() {
        Task blocked = newTask().accepted().started().blocked();
        PhaseTimer blockedPhase = PhaseTimer.opened(blocked.id(), PhaseKind.BLOCKED, CREATED_AT);

        TaskMove move = TaskMove.unblocked(blocked, blockedPhase, null, NOW);

        assertThat(move.task().state()).isEqualTo(TaskState.IN_PROGRESS);
        assertThat(move.transition().statedReason()).isEmpty();
    }

    @Test
    void unblockingSomethingThatWasNeverBlockedIsRefused() {
        Task underway = newTask().accepted().started();

        assertThatThrownBy(() -> TaskMove.unblocked(
                        underway, PhaseTimer.opened(underway.id(), PhaseKind.ACTIVE, CREATED_AT), null, NOW))
                .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void completingClosesTheAssigneesClockAndOpensTheReviewers() {
        Task underway = newTask().accepted().started();
        PhaseTimer active = PhaseTimer.opened(underway.id(), PhaseKind.ACTIVE, CREATED_AT);

        TaskMove move = TaskMove.completed(underway, active, NOW);

        assertThat(move.task().state()).isEqualTo(TaskState.COMPLETED);
        assertThat(move.closedPhase().orElseThrow().kind()).isEqualTo(PhaseKind.ACTIVE);
        assertThat(move.openedPhase().orElseThrow().kind()).isEqualTo(PhaseKind.REVIEW);
        assertThat(move.openedPhase().orElseThrow().countsAgainstTheAssignee())
                .as("review latency is the reviewer's; adding it to the assignee is the defect this prevents")
                .isFalse();
        assertThat(move.event().action()).isEqualTo(TaskAction.TASK_COMPLETED);
    }

    @Test
    void completingABlockedTaskIsRefused() {
        Task blocked = newTask().accepted().started().blocked();

        assertThatThrownBy(() -> TaskMove.completed(
                        blocked, PhaseTimer.opened(blocked.id(), PhaseKind.BLOCKED, CREATED_AT), NOW))
                .isInstanceOf(IllegalTransitionException.class)
                .satisfies(failure -> assertThat(((IllegalTransitionException) failure).from())
                        .isEqualTo(TaskState.BLOCKED));
    }

    @Test
    void completingWorkThatWasNeverStartedIsRefused() {
        Task accepted = newTask().accepted();

        assertThatThrownBy(() ->
                        TaskMove.completed(accepted, PhaseTimer.opened(accepted.id(), PhaseKind.WAIT, CREATED_AT), NOW))
                .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void completingAfterTheDeadlineSucceeds() {
        Task overdue = Task.given(
                        "Draft the supplier review",
                        null,
                        ANDREI,
                        MARIA,
                        CREATED_AT.plusSeconds(60),
                        TaskPriority.NORMAL,
                        CREATED_AT)
                .accepted()
                .started();

        TaskMove move = TaskMove.completed(
                overdue, PhaseTimer.opened(overdue.id(), PhaseKind.ACTIVE, CREATED_AT), NOW.plusSeconds(864_000));

        assertThat(move.task().state()).isEqualTo(TaskState.COMPLETED);
    }

    private static Task newTask() {
        return Task.given(
                "Draft the supplier review",
                null,
                ANDREI,
                MARIA,
                CREATED_AT.plusSeconds(86_400),
                TaskPriority.NORMAL,
                CREATED_AT);
    }
}
