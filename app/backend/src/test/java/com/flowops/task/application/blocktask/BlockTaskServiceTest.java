package com.flowops.task.application.blocktask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.flowops.task.application.shared.port.NotifyTaskProgressPort;
import com.flowops.task.application.shared.port.PhaseTimerPort;
import com.flowops.task.domain.enums.PhaseKind;
import com.flowops.task.domain.enums.TaskAction;
import com.flowops.task.domain.enums.TaskPriority;
import com.flowops.task.domain.enums.TaskState;
import com.flowops.task.domain.exception.BlockReasonRequiredException;
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

@Tag("TASK-BLOCK-01")
@ExtendWith(MockitoExtension.class)
class BlockTaskServiceTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-11T09:00:00Z");
    private static final Instant NOW = Instant.parse("2026-08-11T14:00:00Z");
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

    @Mock
    private NotifyTaskProgressPort notifyTaskProgressPort;

    private BlockTaskService service;

    @BeforeEach
    void buildTheService() {
        service = new BlockTaskService(transitions(), notifyTaskProgressPort);
    }

    @Test
    void blockingStopsTheAssigneesClockAndPutsTheReasonOnTheRecord() {
        Task underway = newTask().accepted().started();
        signedInAs(ANDREI);
        theTaskIs(underway);
        itsOpenPhaseIs(PhaseTimer.opened(underway.id(), PhaseKind.ACTIVE, CREATED_AT));
        theAssigneeIsKnown();

        assertThat(service.execute(new BlockTaskCommand(underway.id(), "The supplier has not sent the figures"))
                        .task()
                        .state())
                .isEqualTo(TaskState.BLOCKED);

        TaskMove move = theMoveWritten();
        assertThat(move.closedPhase().orElseThrow().kind()).isEqualTo(PhaseKind.ACTIVE);
        assertThat(move.openedPhase().orElseThrow().kind()).isEqualTo(PhaseKind.BLOCKED);
        assertThat(move.transition().statedReason()).contains("The supplier has not sent the figures");
        assertThat(move.event().action()).isEqualTo(TaskAction.TASK_BLOCKED);
    }

    @Test
    void theManagerIsNotifiedWithWhatTheWorkIsWaitingOn() {
        Task underway = newTask().accepted().started();
        signedInAs(ANDREI);
        theTaskIs(underway);
        itsOpenPhaseIs(PhaseTimer.opened(underway.id(), PhaseKind.ACTIVE, CREATED_AT));
        theAssigneeIsKnown();

        service.execute(new BlockTaskCommand(underway.id(), "The supplier has not sent the figures"));

        verify(notifyTaskProgressPort).blockRaised(underway.id(), ANDREI, "The supplier has not sent the figures");
    }

    @Test
    void blockingWithNoReasonIsRefusedAndWritesNothing() {
        Task underway = newTask().accepted().started();
        signedInAs(ANDREI);
        theTaskIs(underway);
        itsOpenPhaseIs(PhaseTimer.opened(underway.id(), PhaseKind.ACTIVE, CREATED_AT));

        assertThatThrownBy(() -> service.execute(new BlockTaskCommand(underway.id(), "  ")))
                .isInstanceOf(BlockReasonRequiredException.class);

        verifyNoInteractions(taskWriter, notifyTaskProgressPort);
    }

    @Test
    void nobodyButTheAssigneeMayBlock() {
        Task underway = newTask().accepted().started();
        signedInAs(MARIA);
        theTaskIs(underway);

        assertThatThrownBy(() -> service.execute(new BlockTaskCommand(underway.id(), "Waiting on legal")))
                .isInstanceOf(NotTheAssigneeException.class);

        verifyNoInteractions(taskWriter, notifyTaskProgressPort);
    }

    @Test
    void blockingWorkThatWasNeverStartedIsRefusedAndNamesItsState() {
        Task accepted = newTask().accepted();
        signedInAs(ANDREI);
        theTaskIs(accepted);
        itsOpenPhaseIs(PhaseTimer.opened(accepted.id(), PhaseKind.WAIT, CREATED_AT));

        assertThatThrownBy(() -> service.execute(new BlockTaskCommand(accepted.id(), "Waiting on legal")))
                .isInstanceOf(IllegalTransitionException.class)
                .satisfies(failure -> assertThat(((IllegalTransitionException) failure).from())
                        .isEqualTo(TaskState.ACCEPTED));
    }

    @Test
    void aTaskThatDoesNotExistIsRefused() {
        TaskId missing = TaskId.generate();
        signedInAs(ANDREI);
        when(loadTaskPort.lockForTransition(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(new BlockTaskCommand(missing, "Waiting on legal")))
                .isInstanceOf(TaskNotFoundException.class);
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

    private void theAssigneeIsKnown() {
        when(loadPersonPort.describe(ANDREI))
                .thenReturn(Optional.of(new LoadPersonPort.Person(ANDREI, "Andrei Munteanu", true)));
    }

    private TaskMove theMoveWritten() {
        ArgumentCaptor<TaskMove> written = ArgumentCaptor.forClass(TaskMove.class);
        verify(taskWriter).writeMove(written.capture());
        return written.getValue();
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

    private TaskTransitionSupport transitions() {
        return new TaskTransitionSupport(
                identifyCallerPort,
                loadTaskPort,
                phaseTimerPort,
                loadPersonPort,
                taskWriter,
                new AtRiskRule(() -> 24, Clock.fixed(NOW, ZoneOffset.UTC)),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
