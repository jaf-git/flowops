package com.flowops.task.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowops.task.domain.enums.PhaseKind;
import com.flowops.task.domain.enums.TaskAction;
import com.flowops.task.domain.enums.TaskPriority;
import com.flowops.task.domain.enums.TaskState;
import com.flowops.task.domain.exception.DeadlineInThePastException;
import com.flowops.task.domain.exception.NothingChangedException;
import com.flowops.task.domain.exception.TaskIsClosedException;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.PhaseTimer;
import com.flowops.task.domain.model.Task;
import com.flowops.task.domain.model.TaskAmendment;
import com.flowops.task.domain.model.TaskMove;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("TASK-EDIT-01")
class TaskAmendmentTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-11T09:00:00Z");
    private static final Instant NOW = Instant.parse("2026-08-11T14:00:00Z");
    private static final Instant DEADLINE = Instant.parse("2026-08-20T17:00:00Z");
    private static final Instant LATER = Instant.parse("2026-08-27T17:00:00Z");
    private static final PersonId IONUT = PersonId.of(UUID.randomUUID());
    private static final PersonId ANDREI = PersonId.of(UUID.randomUUID());

    @Test
    void movingTheDeadlineChangesTheDateAndNothingElse() {
        Task before = given();

        Task after =
                before.amended(LATER, before.priority(), before.description().orElse(null), NOW);

        assertThat(after.deadline()).isEqualTo(LATER);
        assertThat(after.state()).isEqualTo(before.state());
        assertThat(after.priority()).isEqualTo(before.priority());
        assertThat(after.assignee()).isEqualTo(before.assignee());
        assertThat(after.title()).isEqualTo(before.title());
    }

    @Test
    void editingWorkUnderwayTouchesNoIntervalAtAll() {
        Task underway = given().accepted().started();
        Task after = underway.amended(LATER, underway.priority(), null, NOW);

        TaskMove move = TaskMove.amended(underway, after, IONUT, TaskAction.TASK_EDITED, null, NOW);

        assertThat(after.state()).isEqualTo(TaskState.IN_PROGRESS);
        assertThat(move.closedPhase()).isEmpty();
        assertThat(move.openedPhase()).isEmpty();
        assertThat(move.transition().from()).isEqualTo(TaskState.IN_PROGRESS);
        assertThat(move.transition().to()).isEqualTo(TaskState.IN_PROGRESS);
        assertThat(move.event().action()).isEqualTo(TaskAction.TASK_EDITED);
    }

    @Test
    void aClosedTaskIsRefused() {
        Task closed = given().accepted().started().completed().approvedBy(IONUT).closed();

        assertThatThrownBy(() -> closed.amended(LATER, TaskPriority.HIGH, null, NOW))
                .isInstanceOf(TaskIsClosedException.class);
    }

    @Test
    void aNewDeadlineInThePastIsRefused() {
        assertThatThrownBy(() -> given().amended(Instant.parse("2026-08-01T09:00:00Z"), TaskPriority.NORMAL, null, NOW))
                .isInstanceOf(DeadlineInThePastException.class);
    }

    @Test
    void submittingTheValuesItAlreadyHasIsRefusedAsUnchanged() {
        Task before = given();

        assertThatThrownBy(() -> before.amended(
                        before.deadline(),
                        before.priority(),
                        before.description().orElse(null),
                        NOW))
                .isInstanceOf(NothingChangedException.class);
    }

    @Test
    void clearingADescriptionThatIsAlreadyEmptyIsUnchanged() {
        Task without =
                Task.given("Rebuild the supplier list", null, ANDREI, IONUT, DEADLINE, TaskPriority.NORMAL, CREATED_AT);

        assertThatThrownBy(() -> without.amended(without.deadline(), without.priority(), "   ", NOW))
                .isInstanceOf(NothingChangedException.class);
    }

    @Test
    void changingOnlyThePriorityIsAChange() {
        Task after = given().amended(DEADLINE, TaskPriority.URGENT, "Before the audit", NOW);

        assertThat(after.priority()).isEqualTo(TaskPriority.URGENT);
        assertThat(after.deadline()).isEqualTo(DEADLINE);
    }

    @Test
    void theAmendmentRecordsOnlyTheFieldsThatActuallyMoved() {
        Task before = given();
        Task after =
                before.amended(LATER, before.priority(), before.description().orElse(null), NOW);

        TaskAmendment amendment = TaskAmendment.between(before, after, UUID.randomUUID(), IONUT, NOW);

        assertThat(amendment.formerDeadline()).isEqualTo(DEADLINE);
        assertThat(amendment.newDeadline()).isEqualTo(LATER);
        assertThat(amendment.formerPriority()).isNull();
        assertThat(amendment.newPriority()).isNull();
        assertThat(amendment.formerDescription()).isNull();
        assertThat(amendment.newDescription()).isNull();
        assertThat(amendment.deadlineMoved()).isTrue();
        assertThat(amendment.actor()).isEqualTo(IONUT);
    }

    @Test
    void anAmendmentThatLeavesTheDeadlineAloneSaysSo() {
        Task before = given();
        Task after =
                before.amended(DEADLINE, TaskPriority.HIGH, before.description().orElse(null), NOW);

        TaskAmendment amendment = TaskAmendment.between(before, after, UUID.randomUUID(), IONUT, NOW);

        assertThat(amendment.deadlineMoved()).isFalse();
        assertThat(amendment.formerPriority()).isEqualTo(TaskPriority.NORMAL);
        assertThat(amendment.newPriority()).isEqualTo(TaskPriority.HIGH);
        assertThat(amendment.formerDeadline()).isNull();
        assertThat(amendment.newDeadline()).isNull();
    }

    @Test
    void anAmendmentBetweenTwoIdenticalTasksIsRefusedRatherThanEmpty() {
        Task before = given();

        assertThatThrownBy(() -> TaskAmendment.between(before, before, UUID.randomUUID(), IONUT, NOW))
                .isInstanceOf(NothingChangedException.class);
    }

    @Test
    void aWaitingTaskKeepsTheIntervalItIsWaitingIn() {
        Task before = given();
        PhaseTimer waiting = PhaseTimer.opened(before.id(), PhaseKind.WAIT, CREATED_AT);
        Task after = before.amended(LATER, before.priority(), null, NOW);

        TaskMove move = TaskMove.amended(before, after, IONUT, TaskAction.TASK_EDITED, null, NOW);

        assertThat(move.closedPhase()).isEmpty();
        assertThat(waiting.isOpen()).isTrue();
        assertThat(waiting.startedAt()).isEqualTo(CREATED_AT);
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
