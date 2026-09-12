package com.flowops.task.application.edittask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import com.flowops.task.application.shared.port.LoadPersonPort;
import com.flowops.task.application.shared.port.LoadTaskPort;
import com.flowops.task.application.shared.port.NotifyNegotiationPort;
import com.flowops.task.application.shared.port.SaveAmendmentPort;
import com.flowops.task.application.shared.port.WorkspaceSettingsPort;
import com.flowops.task.domain.enums.TaskAction;
import com.flowops.task.domain.enums.TaskPriority;
import com.flowops.task.domain.enums.TaskState;
import com.flowops.task.domain.exception.DeadlineInThePastException;
import com.flowops.task.domain.exception.NothingChangedException;
import com.flowops.task.domain.exception.TaskIsClosedException;
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

@Tag("TASK-EDIT-01")
@ExtendWith(MockitoExtension.class)
class EditTaskServiceTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-11T09:00:00Z");
    private static final Instant NOW = Instant.parse("2026-08-11T14:00:00Z");
    private static final Instant DEADLINE = Instant.parse("2026-08-20T17:00:00Z");
    private static final Instant LATER = Instant.parse("2026-08-27T17:00:00Z");
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
    private NotifyNegotiationPort notifyNegotiationPort;

    @Mock
    private LoadPersonPort loadPersonPort;

    private EditTaskService service;

    @BeforeEach
    void buildTheService() {
        service = new EditTaskService(
                authorship(),
                taskWriter,
                saveAmendmentPort,
                notifyNegotiationPort,
                loadPersonPort,
                new AtRiskRule(workspaceSettingsPort, Clock.fixed(NOW, ZoneOffset.UTC)));
    }

    @Test
    void movingTheDeadlineUpdatesItAndRecordsBothValues() {
        Task task = newTask();
        signedInAs(IONUT);
        theTaskIs(task);
        theWindowIs(24);

        Task after = service.execute(new EditTaskCommand(task.id(), LATER, TaskPriority.NORMAL, "Before the audit"))
                .task();

        assertThat(after.deadline()).isEqualTo(LATER);

        ArgumentCaptor<TaskAmendment> written = ArgumentCaptor.forClass(TaskAmendment.class);
        verify(saveAmendmentPort).save(written.capture());
        assertThat(written.getValue().formerDeadline()).isEqualTo(DEADLINE);
        assertThat(written.getValue().newDeadline()).isEqualTo(LATER);
    }

    @Test
    void theAssigneeIsToldTheTargetMovedAndFromWhereTo() {
        Task task = newTask();
        signedInAs(IONUT);
        theTaskIs(task);
        theWindowIs(24);

        service.execute(new EditTaskCommand(task.id(), LATER, TaskPriority.NORMAL, "Before the audit"));

        verify(notifyNegotiationPort).deadlineChanged(task.id(), ANDREI, DEADLINE, LATER);
    }

    @Test
    void changingOnlyThePriorityAnnouncesNoDeadlineChange() {
        Task task = newTask();
        signedInAs(IONUT);
        theTaskIs(task);
        theWindowIs(24);

        service.execute(new EditTaskCommand(task.id(), DEADLINE, TaskPriority.URGENT, "Before the audit"));

        verify(notifyNegotiationPort, never()).deadlineChanged(any(), any(), any(), any());
        verify(saveAmendmentPort).save(any());
    }

    @Test
    void editingWorkUnderwayLeavesItsStateAndItsClockAlone() {
        Task underway = newTask().accepted().started();
        signedInAs(IONUT);
        theTaskIs(underway);
        theWindowIs(24);

        Task after = service.execute(new EditTaskCommand(underway.id(), LATER, TaskPriority.NORMAL, "Before the audit"))
                .task();

        assertThat(after.state()).isEqualTo(TaskState.IN_PROGRESS);

        ArgumentCaptor<TaskMove> recorded = ArgumentCaptor.forClass(TaskMove.class);
        verify(taskWriter).writeAmendment(recorded.capture());
        assertThat(recorded.getValue().closedPhase()).isEmpty();
        assertThat(recorded.getValue().openedPhase()).isEmpty();
        assertThat(recorded.getValue().event().action()).isEqualTo(TaskAction.TASK_EDITED);
        verify(taskWriter, never()).writeMove(any());
    }

    @Test
    void submittingWhatItAlreadySaysIsRefusedAndAppendsNothing() {
        Task task = newTask();
        signedInAs(IONUT);
        theTaskIs(task);
        lenient().when(workspaceSettingsPort.atRiskWindowHours()).thenReturn(24);

        assertThatThrownBy(() -> service.execute(
                        new EditTaskCommand(task.id(), DEADLINE, TaskPriority.NORMAL, "Before the audit")))
                .isInstanceOf(NothingChangedException.class);

        verifyNoInteractions(taskWriter, saveAmendmentPort, notifyNegotiationPort);
    }

    @Test
    void aClosedTaskIsRefusedAndNothingIsWritten() {
        Task closed =
                newTask().accepted().started().completed().approvedBy(CRISTINA).closed();
        signedInAs(IONUT);
        theTaskIs(closed);
        lenient().when(workspaceSettingsPort.atRiskWindowHours()).thenReturn(24);

        assertThatThrownBy(() -> service.execute(
                        new EditTaskCommand(closed.id(), LATER, TaskPriority.NORMAL, "Before the audit")))
                .isInstanceOf(TaskIsClosedException.class);

        verifyNoInteractions(taskWriter, saveAmendmentPort, notifyNegotiationPort);
    }

    @Test
    void aDeadlineInThePastIsRefused() {
        Task task = newTask();
        signedInAs(IONUT);
        theTaskIs(task);
        lenient().when(workspaceSettingsPort.atRiskWindowHours()).thenReturn(24);

        assertThatThrownBy(() -> service.execute(new EditTaskCommand(
                        task.id(), Instant.parse("2026-08-01T09:00:00Z"), TaskPriority.NORMAL, "Before the audit")))
                .isInstanceOf(DeadlineInThePastException.class);

        verifyNoInteractions(taskWriter, saveAmendmentPort, notifyNegotiationPort);
    }

    @Test
    void anotherManagerCannotRetargetWorkTheyDidNotAssign() {
        Task task = newTask();
        signedInAs(CRISTINA);
        theTaskIs(task);
        lenient().when(callerPermissionsPort.callerHolds("TASK_VIEW_ANY")).thenReturn(false);

        assertThatThrownBy(() ->
                        service.execute(new EditTaskCommand(task.id(), LATER, TaskPriority.NORMAL, "Before the audit")))
                .isInstanceOf(NotTheCreatorException.class);

        verifyNoInteractions(taskWriter, saveAmendmentPort, notifyNegotiationPort);
    }

    @Test
    void shorteningTheDeadlineIntoTheWindowMakesTheWorkAtRisk() {
        Task task = newTask();
        signedInAs(IONUT);
        theTaskIs(task);
        theWindowIs(48);

        assertThat(service.execute(new EditTaskCommand(
                                task.id(), NOW.plusSeconds(3600), TaskPriority.NORMAL, "Before the audit"))
                        .atRisk())
                .isTrue();
        verify(notifyNegotiationPort).deadlineChanged(task.id(), ANDREI, DEADLINE, NOW.plusSeconds(3600));
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

    @Test
    void movingTheDeadlineRecordsTheAssignerAsTheOneWhoChoseIt() {
        Task task = newTask();
        signedInAs(IONUT);
        theTaskIs(task);
        theWindowIs(24);

        Task after = service.execute(new EditTaskCommand(task.id(), LATER, TaskPriority.NORMAL, "Before the audit"))
                .task();

        assertThat(after.deadlineSetBy()).contains(IONUT);
        assertThat(after.deadlineSetAt()).contains(NOW);
        assertThat(after.deadlineAcknowledgedAt())
                .as("changing a date is the strongest possible way of having read it")
                .isEmpty();
    }

    private void theWindowIs(int hours) {
        when(workspaceSettingsPort.atRiskWindowHours()).thenReturn(hours);
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
