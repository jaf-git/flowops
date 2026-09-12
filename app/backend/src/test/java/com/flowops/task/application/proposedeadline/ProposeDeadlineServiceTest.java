package com.flowops.task.application.proposedeadline;

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
import com.flowops.task.domain.exception.DeadlineInThePastException;
import com.flowops.task.domain.exception.IllegalTransitionException;
import com.flowops.task.domain.exception.ProposalAlreadyOpenException;
import com.flowops.task.domain.exception.ProposalReasonRequiredException;
import com.flowops.task.domain.model.DeadlineProposal;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.PhaseTimer;
import com.flowops.task.domain.model.Task;
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

@Tag("TASK-PROPOSE-DEADLINE-01")
@ExtendWith(MockitoExtension.class)
class ProposeDeadlineServiceTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-11T09:00:00Z");
    private static final Instant NOW = Instant.parse("2026-08-11T14:00:00Z");
    private static final Instant DEADLINE = Instant.parse("2026-08-20T17:00:00Z");
    private static final Instant NEXT_MONDAY = Instant.parse("2026-08-24T17:00:00Z");
    private static final PersonId IONUT = PersonId.of(UUID.randomUUID());
    private static final PersonId ANDREI = PersonId.of(UUID.randomUUID());
    private static final String BECAUSE = "The parts arrive Friday";

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

    private ProposeDeadlineService service;

    @BeforeEach
    void buildTheService() {
        service = new ProposeDeadlineService(
                transitions(), loadDeadlineProposalPort, saveDeadlineProposalPort, notifyNegotiationPort);
    }

    @Test
    void aProposalIsRecordedOpenAndTheTaskDoesNotMove() {
        Task waiting = newTask();
        signedInAs(ANDREI);
        theTaskIs(waiting);
        itsOpenPhaseIs(PhaseTimer.opened(waiting.id(), PhaseKind.WAIT, CREATED_AT));
        nothingIsOpenOn(waiting);

        Task after = service.execute(new ProposeDeadlineCommand(waiting.id(), NEXT_MONDAY, BECAUSE))
                .task();

        assertThat(after.state()).isEqualTo(TaskState.CREATED);
        assertThat(after.deadline()).isEqualTo(DEADLINE);

        ArgumentCaptor<DeadlineProposal> saved = ArgumentCaptor.forClass(DeadlineProposal.class);
        verify(saveDeadlineProposalPort).save(saved.capture());
        assertThat(saved.getValue().isOpen()).isTrue();
        assertThat(saved.getValue().proposedDeadline()).isEqualTo(NEXT_MONDAY);
        assertThat(saved.getValue().proposer()).isEqualTo(ANDREI);
    }

    @Test
    void theClockKeepsRunningAndNoIntervalIsTouched() {
        Task waiting = newTask();
        signedInAs(ANDREI);
        theTaskIs(waiting);
        itsOpenPhaseIs(PhaseTimer.opened(waiting.id(), PhaseKind.WAIT, CREATED_AT));
        nothingIsOpenOn(waiting);

        service.execute(new ProposeDeadlineCommand(waiting.id(), NEXT_MONDAY, BECAUSE));

        ArgumentCaptor<TaskMove> recorded = ArgumentCaptor.forClass(TaskMove.class);
        verify(taskWriter).writeRecord(recorded.capture());
        assertThat(recorded.getValue().closedPhase()).isEmpty();
        assertThat(recorded.getValue().openedPhase()).isEmpty();
        assertThat(recorded.getValue().event().action()).isEqualTo(TaskAction.DEADLINE_PROPOSED);
        verify(taskWriter, never()).writeMove(any());
        verify(taskWriter, never()).writeAmendment(any());
    }

    @Test
    void theAssignerIsToldWhatWasAskedForAndWhy() {
        Task waiting = newTask();
        signedInAs(ANDREI);
        theTaskIs(waiting);
        itsOpenPhaseIs(PhaseTimer.opened(waiting.id(), PhaseKind.WAIT, CREATED_AT));
        nothingIsOpenOn(waiting);

        service.execute(new ProposeDeadlineCommand(waiting.id(), NEXT_MONDAY, BECAUSE));

        verify(notifyNegotiationPort).deadlineProposed(waiting.id(), IONUT, NEXT_MONDAY, BECAUSE);
    }

    @Test
    void aSecondProposalIsRefusedAndNamesTheOneAlreadyWaiting() {
        Task waiting = newTask();
        signedInAs(ANDREI);
        theTaskIs(waiting);
        itsOpenPhaseIs(PhaseTimer.opened(waiting.id(), PhaseKind.WAIT, CREATED_AT));
        when(loadDeadlineProposalPort.lockOpenProposalOf(waiting.id()))
                .thenReturn(Optional.of(DeadlineProposal.proposed(waiting.id(), NEXT_MONDAY, BECAUSE, ANDREI, NOW)));

        assertThatThrownBy(() -> service.execute(
                        new ProposeDeadlineCommand(waiting.id(), Instant.parse("2026-08-26T17:00:00Z"), "Again")))
                .isInstanceOf(ProposalAlreadyOpenException.class)
                .extracting(failure -> ((ProposalAlreadyOpenException) failure).openProposalDeadline())
                .isEqualTo(NEXT_MONDAY);

        verifyNoInteractions(saveDeadlineProposalPort, notifyNegotiationPort);
    }

    @Test
    void aProposalWithNoReasonIsRefusedAndWritesNothing() {
        Task waiting = newTask();
        signedInAs(ANDREI);
        theTaskIs(waiting);
        itsOpenPhaseIs(PhaseTimer.opened(waiting.id(), PhaseKind.WAIT, CREATED_AT));
        nothingIsOpenOn(waiting);

        assertThatThrownBy(() -> service.execute(new ProposeDeadlineCommand(waiting.id(), NEXT_MONDAY, "  ")))
                .isInstanceOf(ProposalReasonRequiredException.class);

        verifyNoInteractions(saveDeadlineProposalPort, notifyNegotiationPort);
    }

    @Test
    void aProposedDateInThePastIsRefused() {
        Task waiting = newTask();
        signedInAs(ANDREI);
        theTaskIs(waiting);
        itsOpenPhaseIs(PhaseTimer.opened(waiting.id(), PhaseKind.WAIT, CREATED_AT));
        nothingIsOpenOn(waiting);

        assertThatThrownBy(() -> service.execute(
                        new ProposeDeadlineCommand(waiting.id(), Instant.parse("2026-08-01T09:00:00Z"), BECAUSE)))
                .isInstanceOf(DeadlineInThePastException.class);

        verifyNoInteractions(saveDeadlineProposalPort, notifyNegotiationPort);
    }

    @Test
    void nobodyButTheAssigneeMayPropose() {
        Task waiting = newTask();
        signedInAs(IONUT);
        theTaskIs(waiting);

        assertThatThrownBy(() -> service.execute(new ProposeDeadlineCommand(waiting.id(), NEXT_MONDAY, BECAUSE)))
                .isInstanceOf(NotTheAssigneeException.class);

        verifyNoInteractions(saveDeadlineProposalPort, notifyNegotiationPort);
    }

    @Test
    void workAlreadyAcceptedCannotHaveItsDateProposedAgainstHere() {
        Task accepted = newTask().accepted();
        signedInAs(ANDREI);
        theTaskIs(accepted);
        itsOpenPhaseIs(PhaseTimer.opened(accepted.id(), PhaseKind.WAIT, CREATED_AT));

        assertThatThrownBy(() -> service.execute(new ProposeDeadlineCommand(accepted.id(), NEXT_MONDAY, BECAUSE)))
                .isInstanceOf(IllegalTransitionException.class);

        verifyNoInteractions(saveDeadlineProposalPort, notifyNegotiationPort);
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

    private void signedInAs(PersonId person) {
        when(identifyCallerPort.currentCaller()).thenReturn(Optional.of(person));
    }

    private void theTaskIs(Task task) {
        when(loadTaskPort.lockForTransition(task.id())).thenReturn(Optional.of(task));
    }

    private void itsOpenPhaseIs(PhaseTimer phase) {
        when(phaseTimerPort.openPhaseOf(phase.task())).thenReturn(Optional.of(phase));
    }

    private void nothingIsOpenOn(Task task) {
        when(loadDeadlineProposalPort.lockOpenProposalOf(task.id())).thenReturn(Optional.empty());
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
