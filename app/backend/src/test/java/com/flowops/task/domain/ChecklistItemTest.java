package com.flowops.task.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowops.task.domain.exception.ChecklistTextRequiredException;
import com.flowops.task.domain.model.ChecklistItem;
import com.flowops.task.domain.model.ChecklistItemId;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.TaskId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("TASK-CHECKLIST-01")
class ChecklistItemTest {
    private static final Instant WRITTEN = Instant.parse("2026-08-13T09:00:00Z");
    private static final Instant TICKED = Instant.parse("2026-08-13T15:30:00Z");

    private static ChecklistItem aStep() {
        return ChecklistItem.written(
                TaskId.generate(), 0, "Reconcile the ledger", PersonId.of(UUID.randomUUID()), WRITTEN);
    }

    @Test
    void aStepWithNothingWrittenOnItIsRefused() {
        TaskId task = TaskId.generate();
        PersonId andrei = PersonId.of(UUID.randomUUID());

        assertThatThrownBy(() -> ChecklistItem.written(task, 0, null, andrei, WRITTEN))
                .isInstanceOf(ChecklistTextRequiredException.class);
        assertThatThrownBy(() -> ChecklistItem.written(task, 0, "", andrei, WRITTEN))
                .isInstanceOf(ChecklistTextRequiredException.class);
        assertThatThrownBy(() -> ChecklistItem.written(task, 0, "   ", andrei, WRITTEN))
                .as("trimmed before it is judged, or a step of spaces passes and renders as a gap")
                .isInstanceOf(ChecklistTextRequiredException.class);
    }

    @Test
    void theTextIsTrimmedBeforeItIsStored() {
        ChecklistItem step = ChecklistItem.written(
                TaskId.generate(), 0, "  Reconcile the ledger  ", PersonId.of(UUID.randomUUID()), WRITTEN);

        assertThat(step.text()).isEqualTo("Reconcile the ledger");
    }

    @Test
    void aNewStepStartsUntickedWithNoMoment() {
        ChecklistItem step = aStep();

        assertThat(step.isDone()).isFalse();
        assertThat(step.doneAt()).isEmpty();
        assertThat(step.position()).isZero();
    }

    @Test
    void tickingRecordsTheMomentItHappened() {
        ChecklistItem ticked = aStep().ticked(true, TICKED);

        assertThat(ticked.isDone()).isTrue();
        assertThat(ticked.doneAt()).contains(TICKED);
    }

    @Test
    void untickingClearsTheMoment() {
        ChecklistItem unticked = aStep().ticked(true, TICKED).ticked(false, Instant.parse("2026-08-13T16:00:00Z"));

        assertThat(unticked.isDone()).isFalse();
        assertThat(unticked.doneAt()).isEmpty();
    }

    @Test
    void aDoneStepWithNoMomentIsRefusedEvenFromStorage() {
        assertThatThrownBy(() -> ChecklistItem.rebuild(
                        ChecklistItemId.generate(),
                        TaskId.generate(),
                        0,
                        "Reconcile the ledger",
                        true,
                        null,
                        PersonId.of(UUID.randomUUID()),
                        WRITTEN))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("a done step says when");
    }

    @Test
    void anUntickedStepCarriesNoMomentEvenWhenOneIsOffered() {
        ChecklistItem step = ChecklistItem.rebuild(
                ChecklistItemId.generate(),
                TaskId.generate(),
                0,
                "Reconcile the ledger",
                false,
                TICKED,
                PersonId.of(UUID.randomUUID()),
                WRITTEN);

        assertThat(step.doneAt())
                .as("not done, so there is no moment at which it was")
                .isEmpty();
    }
}
