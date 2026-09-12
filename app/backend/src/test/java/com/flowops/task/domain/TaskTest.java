package com.flowops.task.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowops.task.domain.enums.TaskPriority;
import com.flowops.task.domain.enums.TaskState;
import com.flowops.task.domain.exception.DeadlineInThePastException;
import com.flowops.task.domain.exception.DeadlineRequiredException;
import com.flowops.task.domain.exception.DeadlineRequiredToStartException;
import com.flowops.task.domain.exception.TaskTitleRequiredException;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.Task;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("TASK-CREATE-01")
class TaskTest {
    private static final Instant NOW = Instant.parse("2026-08-10T09:00:00Z");
    private static final PersonId MARIA = PersonId.of(UUID.randomUUID());
    private static final PersonId ANDREI = PersonId.of(UUID.randomUUID());

    @Test
    void aTaskIsAlwaysBornInCreated() {
        Task task = given("Draft the supplier review", ANDREI, NOW.plusSeconds(86_400));

        assertThat(task.state()).isEqualTo(TaskState.CREATED);
        assertThat(task.createdAt()).isEqualTo(NOW);
    }

    @Test
    void aTitleOfSpacesIsRefusedRatherThanStored() {
        assertThatThrownBy(() -> given("   ", ANDREI, NOW.plusSeconds(86_400)))
                .isInstanceOf(TaskTitleRequiredException.class);
    }

    @Test
    void aTitleIsTrimmedBeforeItIsStored() {
        assertThat(given("  Draft the supplier review  ", ANDREI, NOW.plusSeconds(86_400))
                        .title())
                .isEqualTo("Draft the supplier review");
    }

    @Test
    void aDeadlineThatHasAlreadyPassedIsRefused() {
        assertThatThrownBy(() -> given("Draft the supplier review", ANDREI, NOW.minusSeconds(1)))
                .isInstanceOf(DeadlineInThePastException.class);
    }

    @Test
    void aDeadlineAtThisVeryInstantIsRefusedToo() {
        assertThatThrownBy(() -> given("Draft the supplier review", ANDREI, NOW))
                .isInstanceOf(DeadlineInThePastException.class);
    }

    @Test
    void aTaskWithNoDeadlineIsCreatedUndatedRatherThanRefused() {
        Task task = given("Draft the supplier review", ANDREI, null);

        assertThat(task.deadline()).isNull();
        assertThat(task.state()).isEqualTo(TaskState.CREATED);
    }

    @Test
    void workCannotBeginOnATaskWithNoDeadline() {
        Task accepted = given("Draft the supplier review", ANDREI, null).accepted();

        assertThatThrownBy(accepted::started).isInstanceOf(DeadlineRequiredToStartException.class);
    }

    @Test
    void workBeginsOnceTheDateExists() {
        Task dated = given("Draft the supplier review", ANDREI, null)
                .accepted()
                .withDeadline(NOW.plusSeconds(86_400), ANDREI, NOW);

        assertThat(dated.started().state()).isEqualTo(TaskState.IN_PROGRESS);
    }

    @Test
    void aDateRecordsWhoChoseItAndWhen() {
        Task dated = given("Draft the supplier review", ANDREI, null)
                .accepted()
                .withDeadline(NOW.plusSeconds(86_400), ANDREI, NOW);

        assertThat(dated.deadline()).isEqualTo(NOW.plusSeconds(86_400));
        assertThat(dated.deadlineSetBy()).contains(ANDREI);
        assertThat(dated.deadlineSetAt()).contains(NOW);
    }

    @Test
    void aDeadlineOnceSetIsNeverUnset() {
        Task dated = given("Draft the supplier review", ANDREI, NOW.plusSeconds(86_400));

        assertThatThrownBy(() -> dated.withDeadline(null, ANDREI, NOW)).isInstanceOf(DeadlineRequiredException.class);
    }

    @Test
    void givingYourselfWorkIsPermittedAndFlagged() {
        assertThat(given("Draft the supplier review", MARIA, NOW.plusSeconds(86_400))
                        .isSelfAssigned())
                .isTrue();
    }

    @Test
    void workGivenToSomebodyElseIsNotFlagged() {
        assertThat(given("Draft the supplier review", ANDREI, NOW.plusSeconds(86_400))
                        .isSelfAssigned())
                .isFalse();
    }

    @Test
    void aBlankDescriptionIsStoredAsNoDescriptionRatherThanAsAnEmptyString() {
        Task task = Task.given(
                "Draft the supplier review", "   ", ANDREI, MARIA, NOW.plusSeconds(86_400), TaskPriority.NORMAL, NOW);

        assertThat(task.description()).isEmpty();
    }

    @Test
    void onlyThePersonItWasGivenToIsItsAssignee() {
        Task task = given("Draft the supplier review", ANDREI, NOW.plusSeconds(86_400));

        assertThat(task.isAssignedTo(ANDREI)).isTrue();
        assertThat(task.isAssignedTo(MARIA)).isFalse();
    }

    private static Task given(String title, PersonId assignee, Instant deadline) {
        return Task.given(
                title, "Compare last quarter against this one.", assignee, MARIA, deadline, TaskPriority.NORMAL, NOW);
    }
}
