package com.flowops.task.application.closetask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowops.task.application.shared.AtRiskRule;
import com.flowops.task.application.shared.TaskReviewSupport;
import com.flowops.task.application.shared.TaskWriter;
import com.flowops.task.application.shared.exception.TaskOutOfScopeException;
import com.flowops.task.application.shared.port.CallerPermissionsPort;
import com.flowops.task.application.shared.port.IdentifyCallerPort;
import com.flowops.task.application.shared.port.LoadPersonPort;
import com.flowops.task.application.shared.port.LoadTaskPort;
import com.flowops.task.application.shared.port.PhaseTimerPort;
import com.flowops.task.application.shared.port.ReportingLinePort;
import com.flowops.task.domain.enums.PhaseKind;
import com.flowops.task.domain.enums.TaskAction;
import com.flowops.task.domain.enums.TaskPriority;
import com.flowops.task.domain.enums.TaskState;
import com.flowops.task.domain.exception.IllegalTransitionException;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.PhaseTimer;
import com.flowops.task.domain.model.Task;
import com.flowops.task.domain.model.TaskMove;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("TASK-CLOSE-01")
@ExtendWith(MockitoExtension.class)
class CloseTaskServiceTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-10T09:00:00Z");
    private static final Instant NOW = Instant.parse("2026-08-11T14:00:00Z");
    private static final PersonId MARIA = PersonId.of(UUID.randomUUID());
    private static final PersonId IONUT = PersonId.of(UUID.randomUUID());
    private static final PersonId ANDREI = PersonId.of(UUID.randomUUID());

    @Mock
    private IdentifyCallerPort identifyCallerPort;

    @Mock
    private CallerPermissionsPort callerPermissionsPort;

    @Mock
    private ReportingLinePort reportingLinePort;

    @Mock
    private LoadTaskPort loadTaskPort;

    @Mock
    private PhaseTimerPort phaseTimerPort;

    @Mock
    private LoadPersonPort loadPersonPort;

    @Mock
    private TaskWriter taskWriter;

    private CloseTaskService service;

    @BeforeEach
    void buildTheService() {
        service = new CloseTaskService(reviews());
    }

    @Test
    void closingEndsTheApprovalPhaseAndOpensNothing() {
        Task approved = approved();
        ionutIsTheCloserOf(approved);
        theAssigneeIsKnown();

        assertThat(service.execute(new CloseTaskCommand(approved.id())).task().state())
                .isEqualTo(TaskState.CLOSED);

        TaskMove move = theMoveWritten();
        assertThat(move.closedPhase().orElseThrow().kind()).isEqualTo(PhaseKind.APPROVAL);
        assertThat(move.openedPhase()).isEmpty();
        assertThat(move.event().action()).isEqualTo(TaskAction.TASK_CLOSED);
    }

    @Test
    void aPersonMayCloseWorkThatWasAssignedToThem() {
        Task mine = Task.given(
                        "Draft the supplier review",
                        null,
                        IONUT,
                        MARIA,
                        CREATED_AT.plusSeconds(86_400),
                        TaskPriority.NORMAL,
                        CREATED_AT)
                .accepted()
                .started()
                .completed()
                .approvedBy(MARIA);
        signedInAs(IONUT);
        theCallerSeesEverything();
        theTaskIs(mine);
        itsOpenPhaseIs(PhaseTimer.opened(mine.id(), PhaseKind.APPROVAL, CREATED_AT));
        when(loadPersonPort.describe(IONUT))
                .thenReturn(Optional.of(new LoadPersonPort.Person(IONUT, "Ionuț Petrescu", true)));

        assertThat(service.execute(new CloseTaskCommand(mine.id())).task().state())
                .isEqualTo(TaskState.CLOSED);
    }

    @Test
    void closingWorkThatWasNeverApprovedIsRefusedAndNamesItsState() {
        Task completed = task().accepted().started().completed();
        signedInAs(IONUT);
        theCallerSeesEverything();
        theTaskIs(completed);
        itsOpenPhaseIs(PhaseTimer.opened(completed.id(), PhaseKind.REVIEW, CREATED_AT));

        assertThatThrownBy(() -> service.execute(new CloseTaskCommand(completed.id())))
                .isInstanceOf(IllegalTransitionException.class)
                .satisfies(failure -> assertThat(((IllegalTransitionException) failure).from())
                        .isEqualTo(TaskState.COMPLETED));

        verify(taskWriter, never()).writeMove(any());
    }

    @Test
    void closingATaskThatIsAlreadyClosedIsRefusedAndAppendsNothing() {
        Task closed = approved().closed();
        signedInAs(IONUT);
        theCallerSeesEverything();
        theTaskIs(closed);

        assertThatThrownBy(() -> service.execute(new CloseTaskCommand(closed.id())))
                .isInstanceOf(IllegalTransitionException.class)
                .satisfies(failure -> assertThat(((IllegalTransitionException) failure).from())
                        .isEqualTo(TaskState.CLOSED));

        verify(taskWriter, never()).writeMove(any());
    }

    @Test
    void aManagerMayNotCloseWorkOutsideTheirOwnSubtree() {
        Task approved = approved();
        signedInAs(IONUT);
        when(callerPermissionsPort.callerHolds("TASK_VIEW_ANY")).thenReturn(false);
        when(reportingLinePort.subtreeOf(IONUT)).thenReturn(Set.of(MARIA));
        theTaskIs(approved);

        assertThatThrownBy(() -> service.execute(new CloseTaskCommand(approved.id())))
                .isInstanceOf(TaskOutOfScopeException.class);

        verify(taskWriter, never()).writeMove(any());
    }

    private void ionutIsTheCloserOf(Task task) {
        signedInAs(IONUT);
        theCallerSeesEverything();
        theTaskIs(task);
        itsOpenPhaseIs(PhaseTimer.opened(task.id(), PhaseKind.APPROVAL, CREATED_AT));
    }

    private void signedInAs(PersonId person) {
        when(identifyCallerPort.currentCaller()).thenReturn(Optional.of(person));
    }

    private void theCallerSeesEverything() {
        when(callerPermissionsPort.callerHolds("TASK_VIEW_ANY")).thenReturn(true);
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

    private static Task task() {
        return Task.given(
                "Draft the supplier review",
                null,
                ANDREI,
                MARIA,
                CREATED_AT.plusSeconds(86_400),
                TaskPriority.NORMAL,
                CREATED_AT);
    }

    private static Task approved() {
        return task().accepted().started().completed().approvedBy(IONUT);
    }

    private TaskReviewSupport reviews() {
        return new TaskReviewSupport(
                identifyCallerPort,
                callerPermissionsPort,
                reportingLinePort,
                loadTaskPort,
                phaseTimerPort,
                loadPersonPort,
                taskWriter,
                new AtRiskRule(() -> 24, Clock.fixed(NOW, ZoneOffset.UTC)),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
