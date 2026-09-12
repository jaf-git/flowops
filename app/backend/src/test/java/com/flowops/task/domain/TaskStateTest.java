package com.flowops.task.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowops.task.domain.enums.PhaseKind;
import com.flowops.task.domain.enums.TaskState;
import com.flowops.task.domain.exception.IllegalTransitionException;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("TASK-ACCEPT-01")
class TaskStateTest {
    private static final List<List<TaskState>> LEGAL = List.of(
            List.of(TaskState.CREATED, TaskState.CREATED),
            List.of(TaskState.CREATED, TaskState.ACCEPTED),
            List.of(TaskState.ACCEPTED, TaskState.IN_PROGRESS),
            List.of(TaskState.IN_PROGRESS, TaskState.BLOCKED),
            List.of(TaskState.IN_PROGRESS, TaskState.COMPLETED),
            List.of(TaskState.BLOCKED, TaskState.IN_PROGRESS),
            List.of(TaskState.COMPLETED, TaskState.IN_PROGRESS),
            List.of(TaskState.COMPLETED, TaskState.APPROVED),
            List.of(TaskState.APPROVED, TaskState.CLOSED));

    @Test
    void aCreatedTaskMayBeAccepted() {
        assertThat(TaskState.CREATED.moveTo(TaskState.ACCEPTED)).isEqualTo(TaskState.ACCEPTED);
    }

    @Test
    void everyPairNotInTheDiagramIsRefused() {
        for (TaskState from : TaskState.values()) {
            for (TaskState to : TaskState.values()) {
                boolean drawn = LEGAL.contains(List.of(from, to));
                assertThat(from.mayBecome(to)).as("%s to %s", from, to).isEqualTo(drawn);
            }
        }
    }

    @Test
    void theRefusalCarriesTheStateTheTaskWasActuallyIn() {
        assertThatThrownBy(() -> TaskState.ACCEPTED.moveTo(TaskState.ACCEPTED))
                .isInstanceOf(IllegalTransitionException.class)
                .satisfies(failure -> assertThat(((IllegalTransitionException) failure).from())
                        .isEqualTo(TaskState.ACCEPTED));
    }

    @Test
    void everyStateNamesThePhaseItOccupies() {
        assertThat(TaskState.CREATED.openPhase()).contains(PhaseKind.WAIT);
        assertThat(TaskState.ACCEPTED.openPhase()).contains(PhaseKind.WAIT);
        assertThat(TaskState.IN_PROGRESS.openPhase()).contains(PhaseKind.ACTIVE);
        assertThat(TaskState.BLOCKED.openPhase()).contains(PhaseKind.BLOCKED);
        assertThat(TaskState.COMPLETED.openPhase()).contains(PhaseKind.REVIEW);
        assertThat(TaskState.APPROVED.openPhase()).contains(PhaseKind.APPROVAL);
        assertThat(TaskState.CLOSED.openPhase())
                .as("a finished task accrues nothing")
                .isEmpty();
    }

    @Test
    void onlyActiveTimeCountsAgainstTheAssignee() {
        for (PhaseKind kind : PhaseKind.values()) {
            assertThat(kind.countsAgainstTheAssignee()).as("%s", kind).isEqualTo(kind == PhaseKind.ACTIVE);
        }
    }
}
