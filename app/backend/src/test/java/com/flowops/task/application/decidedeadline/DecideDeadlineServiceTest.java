package com.flowops.task.application.decidedeadline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowops.task.application.shared.AtRiskRule;
import com.flowops.task.application.shared.TaskAuthorshipSupport;
import com.flowops.task.application.shared.TaskWriter;
import com.flowops.task.application.shared.exception.NotTheCreatorException;
import com.flowops.task.application.shared.port.CallerPermissionsPort;
import com.flowops.task.application.shared.port.IdentifyCallerPort;
import com.flowops.task.application.shared.port.LoadDeadlineProposalPort;
import com.flowops.task.application.shared.port.LoadPersonPort;
import com.flowops.task.application.shared.port.LoadTaskPort;
import com.flowops.task.application.shared.port.NotifyNegotiationPort;
import com.flowops.task.application.shared.port.SaveAmendmentPort;
import com.flowops.task.application.shared.port.SaveDeadlineProposalPort;
import com.flowops.task.application.shared.port.WorkspaceSettingsPort;
import com.flowops.task.domain.enums.ProposalDecision;
import com.flowops.task.domain.enums.TaskAction;
import com.flowops.task.domain.enums.TaskPriority;
import com.flowops.task.domain.exception.DeclineReasonRequiredException;
import com.flowops.task.domain.exception.NoOpenProposalException;
import com.flowops.task.domain.exception.ProposalIsStaleException;
import com.flowops.task.domain.model.DeadlineProposal;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.Task;
import com.flowops.task.domain.model.TaskAmendment;
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

@Tag("TASK-DECIDE-DEADLINE-01")
@ExtendWith(MockitoExtension.class)
class DecideDeadlineServiceTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-11T09:00:00Z");
    private static final Instant NOW = Instant.parse("2026-08-11T14:00:00Z");
    private static final Instant DEADLINE = Instant.parse("2026-08-20T17:00:00Z");
    private static final Instant NEXT_MONDAY = Instant.parse("2026-08-24T17:00:00Z");
    private static final PersonId IONUT = PersonId.of(UUID.randomUUID());
    private static final PersonId CRISTINA = PersonId.of(UUID.randomUUID());
    private static final PersonId ANDREI = PersonId.of(UUID.randomUUID());

    @Mock
    private IdentifyCallerPort identifyCallerPort;

    @Mock
    private CallerPermissionsPort callerPermissionsPort;

    @Mock
    private LoadTaskPort loadTaskPort;

    @Mock
    private WorkspaceSettingsPort workspaceSettingsPort;

    @Mock
    private TaskWriter taskWriter;

    @Mock
    private SaveAmendmentPort saveAmendmentPort;

    @Mock
    private LoadPersonPort loadPersonPort;

    @Mock
    private LoadDeadlineProposalPort loadDeadlineProposalPort;

    @Mock
    private SaveDeadlineProposalPort saveDeadlineProposalPort;

    @Mock
    private NotifyNegotiationPort notifyNegotiationPort;

    private DecideDeadlineService service;

    @BeforeEach
    void buildTheService() {
        service = new DecideDeadlineService(
                authorship(),
                new AtRiskRule(workspaceSettingsPort, Clock.fixed(NOW, ZoneOffset.UTC)),
                taskWriter,
                saveAmendmentPort,
                loadPersonPort,
                loadDeadlineProposalPort,
                saveDeadlineProposalPort,
                notifyNegotiationPort);
    }

    @Test
    void agreeingMovesTheDeadlineAndClosesTheProposal() {
        Task waiting = newTask();
        signedInAs(IONUT);
        theTaskIs(waiting);
        theOpenProposalIs(openOn(waiting));
        theWindowIs(24);

        Task after = service.execute(new DecideDeadlineCommand(waiting.id(), true, null))
                .task();

        assertThat(after.deadline()).isEqualTo(NEXT_MONDAY);

        ArgumentCaptor<DeadlineProposal> answered = ArgumentCaptor.forClass(DeadlineProposal.class);
        verify(saveDeadlineProposalPort).update(answered.capture());
        assertThat(answered.getValue().answer()).contains(ProposalDecision.ACCEPTED);
        assertThat(answered.getValue().decidedBy()).isEqualTo(IONUT);
    }

    @Test
    void theRecordKeepsBothDatesAndTheEventNamesTheChange() {
        Task waiting = newTask();
        signedInAs(IONUT);
        theTaskIs(waiting);
        theOpenProposalIs(openOn(waiting));
        theWindowIs(24);

        service.execute(new DecideDeadlineCommand(waiting.id(), true, null));

        ArgumentCaptor<TaskAmendment> written = ArgumentCaptor.forClass(TaskAmendment.class);
        verify(saveAmendmentPort).save(written.capture());
        assertThat(written.getValue().formerDeadline()).isEqualTo(DEADLINE);
        assertThat(written.getValue().newDeadline()).isEqualTo(NEXT_MONDAY);

        ArgumentCaptor<TaskMove> recorded = ArgumentCaptor.forClass(TaskMove.class);
        verify(taskWriter).writeAmendment(recorded.capture());
        assertThat(recorded.getValue().event().action()).isEqualTo(TaskAction.DEADLINE_CHANGED);
        assertThat(recorded.getValue().closedPhase()).isEmpty();
        assertThat(recorded.getValue().openedPhase()).isEmpty();
    }

    @Test
    void theAssigneeIsToldTheDateWasAgreed() {
        Task waiting = newTask();
        signedInAs(IONUT);
        theTaskIs(waiting);
        theOpenProposalIs(openOn(waiting));
        theWindowIs(24);

        service.execute(new DecideDeadlineCommand(waiting.id(), true, null));

        verify(notifyNegotiationPort).deadlineProposalAccepted(waiting.id(), ANDREI, NEXT_MONDAY);
    }

    @Test
    void refusingLeavesTheDeadlineWhereItWasAndSaysWhy() {
        Task waiting = newTask();
        signedInAs(IONUT);
        theTaskIs(waiting);
        theOpenProposalIs(openOn(waiting));

        Task after = service.execute(
                        new DecideDeadlineCommand(waiting.id(), false, "The client will not move the audit"))
                .task();

        assertThat(after.deadline()).isEqualTo(DEADLINE);
        verify(notifyNegotiationPort)
                .deadlineProposalDeclined(waiting.id(), ANDREI, "The client will not move the audit");
        verify(saveAmendmentPort, never()).save(org.mockito.ArgumentMatchers.any());
        verify(taskWriter, never()).writeAmendment(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void refusingInSilenceIsRefusedAndNothingIsWritten() {
        Task waiting = newTask();
        signedInAs(IONUT);
        theTaskIs(waiting);
        theOpenProposalIs(openOn(waiting));

        assertThatThrownBy(() -> service.execute(new DecideDeadlineCommand(waiting.id(), false, "   ")))
                .isInstanceOf(DeclineReasonRequiredException.class);

        verifyNoInteractions(saveDeadlineProposalPort, notifyNegotiationPort, saveAmendmentPort);
    }

    @Test
    void decidingWhenNothingIsOpenIsRefused() {
        Task waiting = newTask();
        signedInAs(IONUT);
        theTaskIs(waiting);
        when(loadDeadlineProposalPort.lockOpenProposalOf(waiting.id())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(new DecideDeadlineCommand(waiting.id(), true, null)))
                .isInstanceOf(NoOpenProposalException.class);

        verifyNoInteractions(saveDeadlineProposalPort, notifyNegotiationPort, saveAmendmentPort);
    }

    @Test
    void agreeingADateThatExpiredWhileItSatIsRefusedAsStale() {
        Task waiting = newTask();
        signedInAs(IONUT);
        theTaskIs(waiting);

        theOpenProposalIs(DeadlineProposal.proposed(
                waiting.id(),
                Instant.parse("2026-08-08T17:00:00Z"),
                "The parts arrive Friday",
                ANDREI,
                Instant.parse("2026-08-05T09:00:00Z")));
        lenient().when(workspaceSettingsPort.atRiskWindowHours()).thenReturn(24);

        assertThatThrownBy(() -> service.execute(new DecideDeadlineCommand(waiting.id(), true, null)))
                .isInstanceOf(ProposalIsStaleException.class);

        verifyNoInteractions(saveDeadlineProposalPort, notifyNegotiationPort, saveAmendmentPort);
    }

    @Test
    void aDifferentManagerCannotDecideAnothersProposal() {
        Task waiting = newTask();
        signedInAs(CRISTINA);
        theTaskIs(waiting);
        lenient().when(callerPermissionsPort.callerHolds("TASK_VIEW_ANY")).thenReturn(false);

        assertThatThrownBy(() -> service.execute(new DecideDeadlineCommand(waiting.id(), true, null)))
                .isInstanceOf(NotTheCreatorException.class);

        verifyNoInteractions(saveDeadlineProposalPort, notifyNegotiationPort, saveAmendmentPort);
    }

    @Test
    void theNewDateIsMeasuredForRiskWhenTheDecisionIsTaken() {
        Task waiting = newTask();
        signedInAs(IONUT);
        theTaskIs(waiting);
        theOpenProposalIs(openOn(waiting));
        theWindowIs(24 * 365);

        assertThat(service.execute(new DecideDeadlineCommand(waiting.id(), true, null))
                        .atRisk())
                .isTrue();
    }

    private TaskAuthorshipSupport authorship() {
        return new TaskAuthorshipSupport(
                identifyCallerPort,
                callerPermissionsPort,
                loadTaskPort,
                workspaceSettingsPort,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void signedInAs(PersonId person) {
        when(identifyCallerPort.currentCaller()).thenReturn(Optional.of(person));
    }

    private void theTaskIs(Task task) {
        when(loadTaskPort.lockForTransition(task.id())).thenReturn(Optional.of(task));
    }

    private void theOpenProposalIs(DeadlineProposal proposal) {
        when(loadDeadlineProposalPort.lockOpenProposalOf(proposal.task())).thenReturn(Optional.of(proposal));
    }

    private void theWindowIs(int hours) {
        when(workspaceSettingsPort.atRiskWindowHours()).thenReturn(hours);
    }

    private static DeadlineProposal openOn(Task task) {
        return DeadlineProposal.proposed(task.id(), NEXT_MONDAY, "The parts arrive Friday", ANDREI, NOW);
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
