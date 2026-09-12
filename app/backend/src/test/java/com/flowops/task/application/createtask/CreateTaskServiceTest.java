package com.flowops.task.application.createtask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowops.task.application.shared.AtRiskRule;
import com.flowops.task.application.shared.TaskWriter;
import com.flowops.task.application.shared.exception.AssigneeNotActiveException;
import com.flowops.task.application.shared.exception.AssigneeOutOfScopeException;
import com.flowops.task.application.shared.exception.NotAuthenticatedException;
import com.flowops.task.application.shared.port.IdentifyCallerPort;
import com.flowops.task.application.shared.port.LoadPersonPort;
import com.flowops.task.application.shared.port.NotifyAssignmentPort;
import com.flowops.task.application.shared.port.ReportingLinePort;
import com.flowops.task.domain.enums.PhaseKind;
import com.flowops.task.domain.enums.TaskAction;
import com.flowops.task.domain.enums.TaskPriority;
import com.flowops.task.domain.enums.TaskState;
import com.flowops.task.domain.exception.DeadlineInThePastException;
import com.flowops.task.domain.model.PersonId;
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
import org.springframework.context.ApplicationEventPublisher;

@Tag("TASK-CREATE-01")
@ExtendWith(MockitoExtension.class)
class CreateTaskServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-10T09:00:00Z");
    private static final Instant TOMORROW = NOW.plusSeconds(86_400);
    private static final PersonId MARIA = PersonId.of(UUID.randomUUID());
    private static final PersonId ANDREI = PersonId.of(UUID.randomUUID());

    @Mock
    private IdentifyCallerPort identifyCallerPort;

    @Mock
    private LoadPersonPort loadPersonPort;

    @Mock
    private ReportingLinePort reportingLinePort;

    @Mock
    private TaskWriter taskWriter;

    @Mock
    private NotifyAssignmentPort notifyAssignmentPort;

    @Mock
    private ApplicationEventPublisher announcements;

    private CreateTaskService service;

    @BeforeEach
    void buildTheService() {
        service = new CreateTaskService(
                identifyCallerPort,
                loadPersonPort,
                reportingLinePort,
                taskWriter,
                notifyAssignmentPort,
                new AtRiskRule(() -> 24, Clock.fixed(NOW, ZoneOffset.UTC)),
                announcements,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void theWorkIsCreatedWithItsWaitPhaseItsTransitionAndItsEvent() {
        signedInAs(MARIA);
        andreiIs(true);
        inScope(true);

        CreateTaskResult result = service.execute(command(ANDREI, TOMORROW));

        ArgumentCaptor<TaskMove> written = ArgumentCaptor.forClass(TaskMove.class);
        verify(taskWriter).writeCreation(written.capture());
        TaskMove move = written.getValue();

        assertThat(move.task().state()).isEqualTo(TaskState.CREATED);
        assertThat(move.task().assignee()).contains(ANDREI);
        assertThat(move.task().creator())
                .as("the creator comes from the session, never the body")
                .isEqualTo(MARIA);
        assertThat(move.openedPhase().orElseThrow().kind()).isEqualTo(PhaseKind.WAIT);
        assertThat(move.transition().cameFrom()).isEmpty();
        assertThat(move.event().action()).isEqualTo(TaskAction.TASK_CREATED);
        assertThat(result.assigneeName()).isEqualTo("Andrei Munteanu");
    }

    @Test
    void theAssigneeIsNotified() {
        signedInAs(MARIA);
        andreiIs(true);
        inScope(true);

        CreateTaskResult result = service.execute(command(ANDREI, TOMORROW));

        verify(notifyAssignmentPort).assignmentGiven(result.task().id(), ANDREI);
    }

    @Test
    void workCannotBeGivenToSomebodyWhoseAccessHasEnded() {
        signedInAs(MARIA);
        andreiIs(false);

        assertThatThrownBy(() -> service.execute(command(ANDREI, TOMORROW)))
                .isInstanceOf(AssigneeNotActiveException.class);

        verifyNoInteractions(taskWriter, notifyAssignmentPort);
    }

    @Test
    void workCannotBeGivenToSomebodyWhoIsNotThere() {
        signedInAs(MARIA);
        when(loadPersonPort.describe(ANDREI)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(command(ANDREI, TOMORROW)))
                .isInstanceOf(AssigneeNotActiveException.class);

        verifyNoInteractions(taskWriter);
    }

    @Test
    void aManagerCannotGiveWorkOutsideTheirOwnSubtree() {
        signedInAs(MARIA);
        andreiIs(true);
        inScope(false);

        assertThatThrownBy(() -> service.execute(command(ANDREI, TOMORROW)))
                .isInstanceOf(AssigneeOutOfScopeException.class);

        verifyNoInteractions(taskWriter, notifyAssignmentPort);
    }

    @Test
    void theAssigneeIsCheckedBeforeTheDeadlineIs() {
        signedInAs(MARIA);
        andreiIs(false);

        assertThatThrownBy(() -> service.execute(command(ANDREI, NOW.minusSeconds(60))))
                .isInstanceOf(AssigneeNotActiveException.class);
    }

    @Test
    void aDeadlineThatHasPassedIsStillRefused() {
        signedInAs(MARIA);
        andreiIs(true);
        inScope(true);

        assertThatThrownBy(() -> service.execute(command(ANDREI, NOW.minusSeconds(60))))
                .isInstanceOf(DeadlineInThePastException.class);

        verify(taskWriter, never()).writeCreation(any());
    }

    @Test
    void givingYourselfWorkIsFlaggedAsSelfAssigned() {
        signedInAs(MARIA);
        when(loadPersonPort.describe(MARIA))
                .thenReturn(Optional.of(new LoadPersonPort.Person(MARIA, "Maria Ionescu", true)));
        when(reportingLinePort.isWithinScopeOf(MARIA, MARIA)).thenReturn(true);

        CreateTaskResult result = service.execute(command(MARIA, TOMORROW));

        assertThat(result.task().isSelfAssigned()).isTrue();
    }

    @Test
    void aCallWithNoSessionBehindItIsRefused() {
        when(identifyCallerPort.currentCaller()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(command(ANDREI, TOMORROW)))
                .isInstanceOf(NotAuthenticatedException.class);

        verifyNoInteractions(loadPersonPort, reportingLinePort, taskWriter);
    }

    private void signedInAs(PersonId person) {
        when(identifyCallerPort.currentCaller()).thenReturn(Optional.of(person));
    }

    private void andreiIs(boolean active) {
        when(loadPersonPort.describe(ANDREI))
                .thenReturn(Optional.of(new LoadPersonPort.Person(ANDREI, "Andrei Munteanu", active)));
    }

    private void inScope(boolean within) {
        when(reportingLinePort.isWithinScopeOf(MARIA, ANDREI)).thenReturn(within);
    }

    private static CreateTaskCommand command(PersonId assignee, Instant deadline) {
        return new CreateTaskCommand(
                "Draft the supplier review",
                "Compare last quarter against this one.",
                assignee.value(),
                deadline,
                TaskPriority.NORMAL.name(),
                null,
                null,
                null);
    }
}
