package com.flowops.task.application.rejecttask;

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
import com.flowops.task.application.shared.port.LoadDeadlineProposalPort;
import com.flowops.task.application.shared.port.LoadPersonPort;
import com.flowops.task.application.shared.port.LoadTaskPort;
import com.flowops.task.application.shared.port.NotifyNegotiationPort;
import com.flowops.task.application.shared.port.PhaseTimerPort;
import com.flowops.task.application.shared.port.SaveDeadlineProposalPort;
import com.flowops.task.domain.enums.PhaseKind;
import com.flowops.task.domain.enums.TaskAction;
import com.flowops.task.domain.enums.TaskPriority;
import com.flowops.task.domain.enums.TaskState;
import com.flowops.task.domain.exception.IllegalTransitionException;
import com.flowops.task.domain.exception.RejectReasonRequiredException;
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

@Tag("TASK-REJECT-01")
@ExtendWith(MockitoExtension.class)
class RejectTaskServiceTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-11T09:00:00Z");
    private static final Instant NOW = Instant.parse("2026-08-11T14:00:00Z");
    private static final Instant DEADLINE = Instant.parse("2026-08-20T17:00:00Z");
    private static final PersonId IONUT = PersonId.of(UUID.randomUUID());
    private static final PersonId ANDREI = PersonId.of(UUID.randomUUID());
    private static final String BECAUSE = "This is Cristina's account, not mine";

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
    private LoadDeadlineProposalPort loadDeadlineProposalPort;

    @Mock
    private SaveDeadlineProposalPort saveDeadlineProposalPort;

    @Mock
    private NotifyNegotiationPort notifyNegotiationPort;

    private RejectTaskService service;

    @BeforeEach
    void buildTheService() {
        service = new RejectTaskService(
                transitions(), loadDeadlineProposalPort, saveDeadlineProposalPort, notifyNegotiationPort);
    }

    @Test
    void decliningReturnsTheWorkUnassignedAndLeavesItInCreated() {
        Task waiting = newTask();
        signedInAs(ANDREI);
        theTaskIs(waiting);
        itsOpenPhaseIs(PhaseTimer.opened(waiting.id(), PhaseKind.WAIT, CREATED_AT));

        Task returned =
                service.execute(new RejectTaskCommand(waiting.id(), BECAUSE)).task();

        assertThat(returned.state()).isEqualTo(TaskState.CREATED);
        assertThat(returned.assignee()).isEmpty();
    }

    @Test
    void theIntervalSpentWaitingOnThatPersonIsClosedAndAFreshOneOpens() {
        Task waiting = newTask();
        signedInAs(ANDREI);
        theTaskIs(waiting);
        itsOpenPhaseIs(PhaseTimer.opened(waiting.id(), PhaseKind.WAIT, CREATED_AT));

        service.execute(new RejectTaskCommand(waiting.id(), BECAUSE));

        TaskMove move = theMoveWritten();
        assertThat(move.closedPhase().orElseThrow().kind()).isEqualTo(PhaseKind.WAIT);
        assertThat(move.openedPhase().orElseThrow().kind()).isEqualTo(PhaseKind.WAIT);
        assertThat(move.transition().statedReason()).contains(BECAUSE);
        assertThat(move.event().action()).isEqualTo(TaskAction.TASK_REJECTED);
    }

    @Test
    void theAssignerIsToldWithTheReason() {
        Task waiting = newTask();
        signedInAs(ANDREI);
        theTaskIs(waiting);
        itsOpenPhaseIs(PhaseTimer.opened(waiting.id(), PhaseKind.WAIT, CREATED_AT));

        service.execute(new RejectTaskCommand(waiting.id(), BECAUSE));

        verify(notifyNegotiationPort).rejected(waiting.id(), IONUT, BECAUSE);
    }

    @Test
    void decliningWithNoReasonIsRefusedAndWritesNothing() {
        Task waiting = newTask();
        signedInAs(ANDREI);
        theTaskIs(waiting);
        itsOpenPhaseIs(PhaseTimer.opened(waiting.id(), PhaseKind.WAIT, CREATED_AT));

        assertThatThrownBy(() -> service.execute(new RejectTaskCommand(waiting.id(), "   ")))
                .isInstanceOf(RejectReasonRequiredException.class);

        verifyNoInteractions(taskWriter, notifyNegotiationPort);
    }

    @Test
    void nobodyButTheAssigneeMayDecline() {
        Task waiting = newTask();
        signedInAs(IONUT);
        theTaskIs(waiting);

        assertThatThrownBy(() -> service.execute(new RejectTaskCommand(waiting.id(), BECAUSE)))
                .isInstanceOf(NotTheAssigneeException.class);

        verifyNoInteractions(taskWriter, notifyNegotiationPort);
    }

    @Test
    void workAlreadyAcceptedCannotBeDeclined() {
        Task accepted = newTask().accepted();
        signedInAs(ANDREI);
        theTaskIs(accepted);
        itsOpenPhaseIs(PhaseTimer.opened(accepted.id(), PhaseKind.WAIT, CREATED_AT));

        assertThatThrownBy(() -> service.execute(new RejectTaskCommand(accepted.id(), BECAUSE)))
                .isInstanceOf(IllegalTransitionException.class);

        verifyNoInteractions(taskWriter, notifyNegotiationPort);
    }

    @Test
    void aTaskThatIsNotThereIsRefusedBeforeAnythingElse() {
        signedInAs(ANDREI);
        TaskId missing = TaskId.generate();
        when(loadTaskPort.lockForTransition(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(new RejectTaskCommand(missing, BECAUSE)))
                .isInstanceOf(TaskNotFoundException.class);

        verifyNoInteractions(taskWriter, notifyNegotiationPort);
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

    private TaskMove theMoveWritten() {
        ArgumentCaptor<TaskMove> written = ArgumentCaptor.forClass(TaskMove.class);
        verify(taskWriter).writeStateAndAssignee(written.capture());
        return written.getValue();
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
                "Rebuild the supplier list",
                "Before the audit",
                ANDREI,
                IONUT,
                DEADLINE,
                TaskPriority.NORMAL,
                CREATED_AT);
    }
}
