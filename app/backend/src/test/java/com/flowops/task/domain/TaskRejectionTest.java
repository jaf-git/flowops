package com.flowops.task.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowops.task.domain.enums.PhaseKind;
import com.flowops.task.domain.enums.TaskAction;
import com.flowops.task.domain.enums.TaskPriority;
import com.flowops.task.domain.enums.TaskState;
import com.flowops.task.domain.exception.IllegalTransitionException;
import com.flowops.task.domain.exception.RejectReasonRequiredException;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.PhaseTimer;
import com.flowops.task.domain.model.Task;
import com.flowops.task.domain.model.TaskMove;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("TASK-REJECT-01")
class TaskRejectionTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-11T09:00:00Z");
    private static final Instant NOW = Instant.parse("2026-08-11T14:00:00Z");
    private static final Instant DEADLINE = Instant.parse("2026-08-20T17:00:00Z");
    private static final PersonId IONUT = PersonId.of(UUID.randomUUID());
    private static final PersonId ANDREI = PersonId.of(UUID.randomUUID());

    @Test
    void decliningClearsTheAssigneeAndLeavesTheTaskInCreated() {
        Task returned = given().rejected();

        assertThat(returned.assignee()).isEmpty();
        assertThat(returned.state()).isEqualTo(TaskState.CREATED);
    }

    @Test
    void decliningIsNotAMoveToSomewhereElse() {
        Task before = given();

        assertThat(before.state().mayBecome(TaskState.CREATED)).isTrue();
        assertThat(before.rejected().state()).isEqualTo(before.state());
    }

    @Test
    void theTaskStillKnowsWhoGaveItOut() {
        assertThat(given().rejected().wasCreatedBy(IONUT)).isTrue();
    }

    @Test
    void workAlreadyAcceptedCannotBeDeclinedAndTheRefusalNamesTheState() {
        assertThatThrownBy(() -> given().accepted().rejected())
                .isInstanceOf(IllegalTransitionException.class)
                .hasMessageContaining(TaskState.ACCEPTED.name());
    }

    @Test
    void workAlreadyUnderwayCannotBeDeclined() {
        assertThatThrownBy(() -> given().accepted().started().rejected())
                .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void theIntervalSpentWaitingOnThatPersonIsClosedAndAFreshOneOpens() {
        Task before = given();
        PhaseTimer waiting = PhaseTimer.opened(before.id(), PhaseKind.WAIT, CREATED_AT);

        TaskMove move = TaskMove.rejected(before, waiting, "This is Cristina's account, not mine", NOW);

        assertThat(move.closedPhase().orElseThrow().kind()).isEqualTo(PhaseKind.WAIT);
        assertThat(move.closedPhase().orElseThrow().endedAt()).isEqualTo(NOW);
        assertThat(move.openedPhase().orElseThrow().kind()).isEqualTo(PhaseKind.WAIT);
        assertThat(move.openedPhase().orElseThrow().startedAt()).isEqualTo(NOW);
        assertThat(move.openedPhase().orElseThrow().id()).isNotEqualTo(waiting.id());
    }

    @Test
    void theReasonTravelsOnTheTransitionAndTheEventNamesTheAction() {
        Task before = given();
        TaskMove move = TaskMove.rejected(
                before,
                PhaseTimer.opened(before.id(), PhaseKind.WAIT, CREATED_AT),
                "The parts are not mine to order",
                NOW);

        assertThat(move.transition().statedReason()).contains("The parts are not mine to order");
        assertThat(move.event().action()).isEqualTo(TaskAction.TASK_REJECTED);
    }

    @Test
    void theRecordNamesThePersonWhoDeclinedItRatherThanNobody() {
        Task before = given();
        TaskMove move = TaskMove.rejected(
                before, PhaseTimer.opened(before.id(), PhaseKind.WAIT, CREATED_AT), "Not my account", NOW);

        assertThat(move.transition().actor()).isEqualTo(ANDREI);
        assertThat(move.event().actor()).isEqualTo(ANDREI);
    }

    @Test
    void aReasonOfSpacesIsNoReason() {
        Task before = given();
        PhaseTimer waiting = PhaseTimer.opened(before.id(), PhaseKind.WAIT, CREATED_AT);

        assertThatThrownBy(() -> TaskMove.rejected(before, waiting, "   ", NOW))
                .isInstanceOf(RejectReasonRequiredException.class);
        assertThatThrownBy(() -> TaskMove.rejected(before, waiting, null, NOW))
                .isInstanceOf(RejectReasonRequiredException.class);
    }

    @Test
    void onceDeclinedTheWorkBelongsToNobodyAtAll() {
        Task returned = given().rejected();

        assertThat(returned.isAssignedTo(ANDREI)).isFalse();
        assertThat(returned.isAssignedTo(IONUT)).isFalse();
    }

    private static Task given() {
        return Task.given(
                "Rebuild the supplier list",
                "Before the audit",
                ANDREI,
                IONUT,
                DEADLINE,
                TaskPriority.NORMAL,
                CREATED_AT);
    }
}
