package com.flowops.task.application.settaskdeadline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowops.task.application.shared.AtRiskRule;
import com.flowops.task.application.shared.TaskTransitionSupport;
import com.flowops.task.application.shared.TaskWriter;
import com.flowops.task.application.shared.exception.NotTheAssigneeException;
import com.flowops.task.application.shared.port.IdentifyCallerPort;
import com.flowops.task.application.shared.port.LoadPersonPort;
import com.flowops.task.application.shared.port.LoadTaskPort;
import com.flowops.task.application.shared.port.PhaseTimerPort;
import com.flowops.task.application.shared.port.SaveAmendmentPort;
import com.flowops.task.application.shared.port.WorkspaceSettingsPort;
import com.flowops.task.domain.enums.PhaseKind;
import com.flowops.task.domain.enums.TaskAction;
import com.flowops.task.domain.enums.TaskPriority;
import com.flowops.task.domain.enums.TaskState;
import com.flowops.task.domain.exception.DeadlineInThePastException;
import com.flowops.task.domain.exception.IllegalTransitionException;
import com.flowops.task.domain.exception.NothingChangedException;
import com.flowops.task.domain.exception.UseAProposalInsteadException;
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

@Tag("TASK-SET-DEADLINE-01")
@ExtendWith(MockitoExtension.class)
class SetTaskDeadlineServiceTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-11T09:00:00Z");
    private static final Instant NOW = Instant.parse("2026-08-11T14:00:00Z");
    private static final Instant IN_THREE_DAYS = NOW.plusSeconds(3 * 86_400);
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
    private SaveAmendmentPort saveAmendmentPort;

    @Mock
    private com.flowops.task.application.shared.port.SaveTaskPort saveTaskPort;

    @Mock
    private WorkspaceSettingsPort workspaceSettingsPort;

    private SetTaskDeadlineService service;

    @BeforeEach
    void buildTheService() {
        service = new SetTaskDeadlineService(transitions(), saveAmendmentPort, saveTaskPort);
    }

    @Test
    void theAssigneeSetsTheDateAndTheTaskDoesNotMove() {
        Task accepted = undated().accepted();
        signedInAs(ANDREI);
        theTaskIs(accepted);
        itsOpenPhaseIs(PhaseTimer.opened(accepted.id(), PhaseKind.WAIT, CREATED_AT));
        theAssigneeIsKnown();
        theWindowIs(24);

        assertThat(service.execute(new SetTaskDeadlineCommand(accepted.id(), IN_THREE_DAYS))
                        .task()
                        .state())
                .as("setting a date is not progress")
                .isEqualTo(TaskState.ACCEPTED);

        TaskMove written = theAmendmentWritten();
        assertThat(written.task().deadline()).isEqualTo(IN_THREE_DAYS);
        assertThat(written.task().deadlineSetBy()).contains(ANDREI);
        assertThat(written.closedPhase())
                .as("no phase boundary: the wait phase keeps running")
                .isEmpty();
        assertThat(written.openedPhase()).isEmpty();
    }

    @Test
    void theEventSaysTheAssigneeChoseTheDateRatherThanThatSomebodyMovedIt() {
        Task accepted = undated().accepted();
        signedInAs(ANDREI);
        theTaskIs(accepted);
        itsOpenPhaseIs(PhaseTimer.opened(accepted.id(), PhaseKind.WAIT, CREATED_AT));
        theAssigneeIsKnown();
        theWindowIs(24);

        service.execute(new SetTaskDeadlineCommand(accepted.id(), IN_THREE_DAYS));

        assertThat(theAmendmentWritten().event().action()).isEqualTo(TaskAction.DEADLINE_SET);
    }

    @Test
    void nobodyButTheAssigneeMaySetTheDate() {
        Task accepted = undated().accepted();
        signedInAs(MARIA);
        theTaskIs(accepted);

        assertThatThrownBy(() -> service.execute(new SetTaskDeadlineCommand(accepted.id(), IN_THREE_DAYS)))
                .isInstanceOf(NotTheAssigneeException.class);

        verify(taskWriter, never()).writeAmendment(any());
    }

    @Test
    void theDateCannotBeSetBeforeTheWorkIsAccepted() {
        Task created = undated();
        signedInAs(ANDREI);
        theTaskIs(created);
        itsOpenPhaseIs(PhaseTimer.opened(created.id(), PhaseKind.WAIT, CREATED_AT));

        assertThatThrownBy(() -> service.execute(new SetTaskDeadlineCommand(created.id(), IN_THREE_DAYS)))
                .isInstanceOf(IllegalTransitionException.class)
                .satisfies(failure -> assertThat(((IllegalTransitionException) failure).from())
                        .isEqualTo(TaskState.CREATED));

        verify(taskWriter, never()).writeAmendment(any());
    }

    @Test
    void onceWorkHasBegunTheRouteIsAProposalAndTheRefusalSaysSo() {
        Task started = dated().accepted().started();
        signedInAs(ANDREI);
        theTaskIs(started);
        itsOpenPhaseIs(PhaseTimer.opened(started.id(), PhaseKind.ACTIVE, CREATED_AT));

        assertThatThrownBy(() -> service.execute(new SetTaskDeadlineCommand(started.id(), IN_THREE_DAYS)))
                .isInstanceOf(UseAProposalInsteadException.class);

        verify(taskWriter, never()).writeAmendment(any());
    }

    @Test
    void aDateInThePastIsRefused() {
        Task accepted = undated().accepted();
        signedInAs(ANDREI);
        theTaskIs(accepted);
        itsOpenPhaseIs(PhaseTimer.opened(accepted.id(), PhaseKind.WAIT, CREATED_AT));

        assertThatThrownBy(() -> service.execute(new SetTaskDeadlineCommand(accepted.id(), NOW.minusSeconds(1))))
                .isInstanceOf(DeadlineInThePastException.class);

        verify(taskWriter, never()).writeAmendment(any());
    }

    @Test
    void settingTheDateItAlreadyCarriesIsRefusedAndAppendsNothing() {
        Task accepted = dated().accepted();
        signedInAs(ANDREI);
        theTaskIs(accepted);
        itsOpenPhaseIs(PhaseTimer.opened(accepted.id(), PhaseKind.WAIT, CREATED_AT));

        assertThatThrownBy(() -> service.execute(new SetTaskDeadlineCommand(accepted.id(), IN_THREE_DAYS)))
                .isInstanceOf(NothingChangedException.class);

        verify(taskWriter, never()).writeAmendment(any());
    }

    @Test
    void theDateMayBeSetAgainWhileTheWorkHasNotBegun() {
        Task accepted = dated().accepted();
        signedInAs(ANDREI);
        theTaskIs(accepted);
        itsOpenPhaseIs(PhaseTimer.opened(accepted.id(), PhaseKind.WAIT, CREATED_AT));
        theAssigneeIsKnown();
        theWindowIs(24);

        Instant later = IN_THREE_DAYS.plusSeconds(86_400);

        assertThat(service.execute(new SetTaskDeadlineCommand(accepted.id(), later))
                        .task()
                        .deadline())
                .isEqualTo(later);
    }

    @Test
    void aDateChosenInsideTheWindowIsReportedAtRiskImmediately() {
        Task accepted = undated().accepted();
        signedInAs(ANDREI);
        theTaskIs(accepted);
        itsOpenPhaseIs(PhaseTimer.opened(accepted.id(), PhaseKind.WAIT, CREATED_AT));
        theAssigneeIsKnown();
        theWindowIs(24 * 7);

        assertThat(service.execute(new SetTaskDeadlineCommand(accepted.id(), IN_THREE_DAYS))
                        .atRisk())
                .isTrue();
    }

    private void signedInAs(PersonId person) {
        when(identifyCallerPort.currentCaller()).thenReturn(Optional.of(person));
    }

    private void theTaskIs(Task task) {
        when(loadTaskPort.lockForTransition(task.id())).thenReturn(Optional.of(task));
    }

    private void itsOpenPhaseIs(PhaseTimer phase) {
        lenient().when(phaseTimerPort.openPhaseOf(phase.task())).thenReturn(Optional.of(phase));
    }

    private void theAssigneeIsKnown() {
        lenient()
                .when(loadPersonPort.describe(ANDREI))
                .thenReturn(Optional.of(new LoadPersonPort.Person(ANDREI, "Andrei Munteanu", true)));
    }

    private void theWindowIs(int hours) {
        lenient().when(workspaceSettingsPort.atRiskWindowHours()).thenReturn(hours);
    }

    private TaskMove theAmendmentWritten() {
        ArgumentCaptor<TaskMove> written = ArgumentCaptor.forClass(TaskMove.class);
        verify(taskWriter).writeAmendment(written.capture());
        return written.getValue();
    }

    private static Task undated() {
        return Task.given("Pregătește dosarul fiscal", null, ANDREI, MARIA, null, TaskPriority.NORMAL, CREATED_AT);
    }

    private static Task dated() {
        return Task.given(
                "Pregătește dosarul fiscal", null, ANDREI, MARIA, IN_THREE_DAYS, TaskPriority.NORMAL, CREATED_AT);
    }

    private TaskTransitionSupport transitions() {
        return new TaskTransitionSupport(
                identifyCallerPort,
                loadTaskPort,
                phaseTimerPort,
                loadPersonPort,
                taskWriter,
                new AtRiskRule(workspaceSettingsPort, Clock.fixed(NOW, ZoneOffset.UTC)),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
