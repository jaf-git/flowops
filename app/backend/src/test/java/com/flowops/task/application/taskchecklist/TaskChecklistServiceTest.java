package com.flowops.task.application.taskchecklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowops.task.application.shared.TaskMaterialSupport;
import com.flowops.task.application.shared.exception.NotTheAssigneeException;
import com.flowops.task.application.shared.exception.TaskNotFoundException;
import com.flowops.task.application.shared.port.AppendTaskEventPort;
import com.flowops.task.application.shared.port.IdentifyCallerPort;
import com.flowops.task.application.shared.port.LoadTaskPort;
import com.flowops.task.application.shared.port.TaskMaterialPort;
import com.flowops.task.domain.enums.TaskPriority;
import com.flowops.task.domain.model.ChecklistItem;
import com.flowops.task.domain.model.ChecklistItemId;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.Task;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("TASK-CHECKLIST-01")
@ExtendWith(MockitoExtension.class)
class TaskChecklistServiceTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-13T09:00:00Z");
    private static final Instant NOW = Instant.parse("2026-08-13T14:00:00Z");
    private static final PersonId IONUT = PersonId.of(UUID.randomUUID());
    private static final PersonId ANDREI = PersonId.of(UUID.randomUUID());

    @Mock
    private IdentifyCallerPort identifyCallerPort;

    @Mock
    private LoadTaskPort loadTaskPort;

    @Mock
    private TaskMaterialPort taskMaterialPort;

    @Mock
    private AppendTaskEventPort appendTaskEventPort;

    private TaskChecklistService service;
    private Task task;

    @BeforeEach
    void buildTheService() {
        task = Task.given("Reconciliază registrul", null, ANDREI, IONUT, null, TaskPriority.NORMAL, CREATED_AT);
        service = new TaskChecklistService(
                new TaskMaterialSupport(identifyCallerPort, loadTaskPort, Clock.fixed(NOW, ZoneOffset.UTC)),
                taskMaterialPort,
                appendTaskEventPort);
    }

    @Test
    void thePersonWhoGaveTheWorkOutMayWriteAStep() {
        callerIs(IONUT);
        when(taskMaterialPort.nextPosition(task.id())).thenReturn(3);

        ChecklistItem written = service.add(new AddChecklistItemCommand(task.id(), "Reconcile the ledger"));

        assertThat(written.position()).isEqualTo(3);
        assertThat(written.authoredBy()).isEqualTo(IONUT);
        assertThat(written.isDone()).isFalse();
        verify(taskMaterialPort).add(written);
        verify(appendTaskEventPort).append(any());
    }

    @Test
    void thePersonWhoGaveTheWorkOutMayNotTickOne() {
        callerIs(IONUT);

        assertThatThrownBy(
                        () -> service.tick(new TickChecklistItemCommand(task.id(), ChecklistItemId.generate(), true)))
                .isInstanceOf(NotTheAssigneeException.class);

        verify(taskMaterialPort, never()).update(any());
        verify(appendTaskEventPort, never()).append(any());
    }

    @Test
    void theAssigneeTicksItAndTheMomentIsRecorded() {
        callerIs(ANDREI);
        ChecklistItem step = aStepOnTheTask();
        when(taskMaterialPort.findItem(task.id(), step.id())).thenReturn(Optional.of(step));

        ChecklistItem ticked = service.tick(new TickChecklistItemCommand(task.id(), step.id(), true));

        assertThat(ticked.isDone()).isTrue();
        assertThat(ticked.doneAt()).contains(NOW);
        verify(taskMaterialPort).update(ticked);
    }

    @Test
    void aStepThisTaskDoesNotHaveIsRefusedAndNothingIsWritten() {
        callerIs(ANDREI);
        ChecklistItemId elsewhere = ChecklistItemId.generate();
        when(taskMaterialPort.findItem(task.id(), elsewhere)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.tick(new TickChecklistItemCommand(task.id(), elsewhere, true)))
                .isInstanceOf(TaskNotFoundException.class);

        verify(taskMaterialPort, never()).update(any());
        verify(appendTaskEventPort, never()).append(any());
    }

    @Test
    void removingAStepThisTaskDoesNotHaveRemovesNothing() {
        callerIs(ANDREI);
        ChecklistItemId elsewhere = ChecklistItemId.generate();
        when(taskMaterialPort.findItem(task.id(), elsewhere)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remove(new RemoveChecklistItemCommand(task.id(), elsewhere)))
                .isInstanceOf(TaskNotFoundException.class);

        verify(taskMaterialPort, never()).remove(any(), any());
        verify(appendTaskEventPort, never()).append(any());
    }

    private void callerIs(PersonId person) {
        when(identifyCallerPort.currentCaller()).thenReturn(Optional.of(person));
        when(loadTaskPort.findById(task.id())).thenReturn(Optional.of(task));
    }

    private ChecklistItem aStepOnTheTask() {
        return ChecklistItem.written(task.id(), 0, "Reconcile the ledger", ANDREI, CREATED_AT);
    }
}
