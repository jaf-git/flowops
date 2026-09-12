package com.flowops.task.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowops.task.domain.enums.PhaseKind;
import com.flowops.task.domain.enums.TaskAction;
import com.flowops.task.domain.enums.TaskPriority;
import com.flowops.task.domain.enums.TaskState;
import com.flowops.task.domain.exception.IllegalTransitionException;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.PhaseTimer;
import com.flowops.task.domain.model.Task;
import com.flowops.task.domain.model.TaskMove;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("TASK-ACCEPT-01")
class TaskMoveTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-10T09:00:00Z");
    private static final Instant ACCEPTED_AT = Instant.parse("2026-08-10T11:30:00Z");
    private static final PersonId MARIA = PersonId.of(UUID.randomUUID());
    private static final PersonId ANDREI = PersonId.of(UUID.randomUUID());

    @Test
    void creationOpensAWaitPhaseAndClosesNothing() {
        TaskMove move = TaskMove.creation(newTask(), CREATED_AT);

        assertThat(move.closedPhase())
                .as("nothing was open before the task existed")
                .isEmpty();
        assertThat(move.openedPhase()).isPresent();
        assertThat(move.openedPhase().get().kind()).isEqualTo(PhaseKind.WAIT);
        assertThat(move.openedPhase().get().isOpen()).isTrue();
        assertThat(move.openedPhase().get().startedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void creationRecordsATransitionThatCameFromNowhereAndLogsIt() {
        TaskMove move = TaskMove.creation(newTask(), CREATED_AT);

        assertThat(move.transition().cameFrom()).isEmpty();
        assertThat(move.transition().to()).isEqualTo(TaskState.CREATED);
        assertThat(move.transition().actor()).isEqualTo(MARIA);
        assertThat(move.event().action()).isEqualTo(TaskAction.TASK_CREATED);
        assertThat(move.event().actor()).isEqualTo(MARIA);
    }

    @Test
    void acceptanceClosesTheWaitPhaseAndOpensNothingThatCountsAgainstTheAssignee() {
        Task task = newTask();
        PhaseTimer wait = PhaseTimer.opened(task.id(), PhaseKind.WAIT, CREATED_AT);

        TaskMove move = TaskMove.acceptance(task, wait, ACCEPTED_AT);

        assertThat(move.task().state()).isEqualTo(TaskState.ACCEPTED);
        assertThat(move.closedPhase()).isPresent();
        assertThat(move.closedPhase().get().id()).isEqualTo(wait.id());
        assertThat(move.closedPhase().get().endedAt()).isEqualTo(ACCEPTED_AT);
        assertThat(move.closedPhase().get().isOpen()).isFalse();
        assertThat(move.openedPhase()).isPresent();
        assertThat(move.openedPhase().get().countsAgainstTheAssignee())
                .as("accepted is not started; nothing may accrue against them yet")
                .isFalse();
    }

    @Test
    void theClosedWaitPhaseRecordsTheWholeIntervalItWasOpen() {
        Task task = newTask();
        PhaseTimer wait = PhaseTimer.opened(task.id(), PhaseKind.WAIT, CREATED_AT);

        TaskMove move = TaskMove.acceptance(task, wait, ACCEPTED_AT);

        assertThat(move.closedPhase().orElseThrow().elapsed())
                .contains(java.time.Duration.between(CREATED_AT, ACCEPTED_AT));
    }

    @Test
    void acceptanceIsRefusedFromAnyStateButCreated() {
        Task accepted = newTask().accepted();
        PhaseTimer wait = PhaseTimer.opened(accepted.id(), PhaseKind.WAIT, CREATED_AT);

        assertThatThrownBy(() -> TaskMove.acceptance(accepted, wait, ACCEPTED_AT))
                .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void acceptanceRecordsWhereItCameFromAndLogsTheAcknowledgement() {
        Task task = newTask();
        TaskMove move =
                TaskMove.acceptance(task, PhaseTimer.opened(task.id(), PhaseKind.WAIT, CREATED_AT), ACCEPTED_AT);

        assertThat(move.transition().cameFrom()).contains(TaskState.CREATED);
        assertThat(move.transition().to()).isEqualTo(TaskState.ACCEPTED);
        assertThat(move.transition().actor())
                .as("the assignee acknowledged it, nobody else")
                .isEqualTo(ANDREI);
        assertThat(move.event().action()).isEqualTo(TaskAction.TASK_ACCEPTED);
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
