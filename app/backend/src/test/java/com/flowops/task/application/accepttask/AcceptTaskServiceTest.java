package com.flowops.task.application.accepttask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowops.task.application.shared.AtRiskRule;
import com.flowops.task.application.shared.TaskTransitionSupport;
import com.flowops.task.application.shared.TaskWriter;
import com.flowops.task.application.shared.exception.NotTheAssigneeException;
import com.flowops.task.application.shared.exception.TaskNotFoundException;
import com.flowops.task.application.shared.port.IdentifyCallerPort;
import com.flowops.task.application.shared.port.LoadPersonPort;
import com.flowops.task.application.shared.port.LoadTaskPort;
import com.flowops.task.application.shared.port.PhaseTimerPort;
import com.flowops.task.domain.enums.PhaseKind;
import com.flowops.task.domain.enums.TaskAction;
import com.flowops.task.domain.enums.TaskPriority;
import com.flowops.task.domain.enums.TaskState;
import com.flowops.task.domain.exception.IllegalTransitionException;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.PhaseTimer;
import com.flowops.task.domain.model.Task;
import com.flowops.task.domain.model.TaskId;
import com.flowops.task.domain.model.TaskMove;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("TASK-ACCEPT-01")
@ExtendWith(MockitoExtension.class)
class AcceptTaskServiceTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-10T09:00:00Z");
    private static final Instant NOW = Instant.parse("2026-08-10T11:30:00Z");
    private static final PersonId MARIA = PersonId.of(UUID.randomUUID());
    private static final PersonId ANDREI = PersonId.of(UUID.randomUUID());

    @Mock
    private IdentifyCallerPort identifyCallerPort;

    @Mock
    private LoadTaskPort loadTaskPort;

    @Mock
    private PhaseTimerPort phaseTimerPort;

    @Mock
    private LoadPersonPort loadPersonPort;

    @Mock
    private TaskWriter taskWriter;

    private AcceptTaskService service;

    @BeforeEach
    void buildTheService() {
        service = new AcceptTaskService(new TaskTransitionSupport(
                identifyCallerPort,
                loadTaskPort,
                phaseTimerPort,
                loadPersonPort,
                taskWriter,
                new AtRiskRule(() -> 24, Clock.fixed(NOW, ZoneOffset.UTC)),
                Clock.fixed(NOW, ZoneOffset.UTC)));
    }

    @Test
    void theAssigneeAcknowledgesAndTheWaitPhaseCloses() {
        Task task = newTask();
        signedInAs(ANDREI);
        theTaskIs(task);
        itsOpenPhaseIs(PhaseTimer.opened(task.id(), PhaseKind.WAIT, CREATED_AT));
        when(loadPersonPort.describe(ANDREI))
                .thenReturn(Optional.of(new LoadPersonPort.Person(ANDREI, "Andrei Munteanu", true)));

        AcceptTaskResult result = service.execute(new AcceptTaskCommand(task.id()));

        ArgumentCaptor<TaskMove> written = ArgumentCaptor.forClass(TaskMove.class);
        verify(taskWriter).writeMove(written.capture());
        TaskMove move = written.getValue();

        assertThat(move.task().state()).isEqualTo(TaskState.ACCEPTED);
        assertThat(move.closedPhase().orElseThrow().endedAt()).isEqualTo(NOW);
        assertThat(move.openedPhase().orElseThrow().countsAgainstTheAssignee())
                .as("accepted is not started")
                .isFalse();
        assertThat(move.event().action()).isEqualTo(TaskAction.TASK_ACCEPTED);
        assertThat(result.task().state()).isEqualTo(TaskState.ACCEPTED);
    }

    @Test
    void nobodyButTheAssigneeMayAcknowledge() {
        Task task = newTask();
        signedInAs(MARIA);
        theTaskIs(task);

        assertThatThrownBy(() -> service.execute(new AcceptTaskCommand(task.id())))
                .isInstanceOf(NotTheAssigneeException.class);

        verifyNoInteractions(taskWriter);
    }

    @Test
    void aTaskThatHasAlreadyMovedOnIsRefusedAndNamesItsState() {
        Task accepted = newTask().accepted();
        signedInAs(ANDREI);
        theTaskIs(accepted);
        itsOpenPhaseIs(PhaseTimer.opened(accepted.id(), PhaseKind.WAIT, CREATED_AT));

        assertThatThrownBy(() -> service.execute(new AcceptTaskCommand(accepted.id())))
                .isInstanceOf(IllegalTransitionException.class)
                .satisfies(failure -> assertThat(((IllegalTransitionException) failure).from())
                        .isEqualTo(TaskState.ACCEPTED));

        verify(taskWriter, never()).writeMove(any());
    }

    @Test
    void acceptingAfterTheDeadlineStillSucceeds() {
        Task overdue = Task.given(
                "Draft the supplier review", null, ANDREI, MARIA, NOW.plusSeconds(60), TaskPriority.NORMAL, CREATED_AT);
        signedInAs(ANDREI);
        theTaskIs(overdue);
        itsOpenPhaseIs(PhaseTimer.opened(overdue.id(), PhaseKind.WAIT, CREATED_AT));
        when(loadPersonPort.describe(ANDREI))
                .thenReturn(Optional.of(new LoadPersonPort.Person(ANDREI, "Andrei Munteanu", true)));

        assertThat(service.execute(new AcceptTaskCommand(overdue.id())).task().state())
                .isEqualTo(TaskState.ACCEPTED);
    }

    @Test
    void aTaskThatDoesNotExistIsRefused() {
        TaskId missing = TaskId.generate();
        signedInAs(ANDREI);
        when(loadTaskPort.lockForTransition(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(new AcceptTaskCommand(missing)))
                .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void theTaskIsReadUnderALockRatherThanPlainly() {
        Task task = newTask();
        signedInAs(ANDREI);
        theTaskIs(task);
        itsOpenPhaseIs(PhaseTimer.opened(task.id(), PhaseKind.WAIT, CREATED_AT));
        when(loadPersonPort.describe(ANDREI))
                .thenReturn(Optional.of(new LoadPersonPort.Person(ANDREI, "Andrei Munteanu", true)));

        service.execute(new AcceptTaskCommand(task.id()));

        verify(loadTaskPort).lockForTransition(task.id());
        verify(loadTaskPort, never()).findById(any());
    }

    private void signedInAs(PersonId person) {
        when(identifyCallerPort.currentCaller()).thenReturn(Optional.of(person));
    }

    private void theTaskIs(Task task) {
        when(loadTaskPort.lockForTransition(task.id())).thenReturn(Optional.of(task));
    }

    private void itsOpenPhaseIs(PhaseTimer phase) {
        when(phaseTimerPort.openPhaseOf(phase.task())).thenReturn(Optional.of(phase));
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
