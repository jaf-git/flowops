package com.flowops.task.application.approvetask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowops.task.application.shared.AtRiskRule;
import com.flowops.task.application.shared.TaskReviewSupport;
import com.flowops.task.application.shared.TaskWriter;
import com.flowops.task.application.shared.exception.TaskNotFoundException;
import com.flowops.task.application.shared.exception.TaskOutOfScopeException;
import com.flowops.task.application.shared.port.CallerPermissionsPort;
import com.flowops.task.application.shared.port.IdentifyCallerPort;
import com.flowops.task.application.shared.port.LoadPersonPort;
import com.flowops.task.application.shared.port.LoadTaskPort;
import com.flowops.task.application.shared.port.NotifyTaskProgressPort;
import com.flowops.task.application.shared.port.PhaseTimerPort;
import com.flowops.task.application.shared.port.ReportingLinePort;
import com.flowops.task.application.shared.port.SaveApprovalPort;
import com.flowops.task.domain.enums.PhaseKind;
import com.flowops.task.domain.enums.TaskAction;
import com.flowops.task.domain.enums.TaskPriority;
import com.flowops.task.domain.enums.TaskState;
import com.flowops.task.domain.exception.ApprovalScoreOutOfRangeException;
import com.flowops.task.domain.exception.ApprovalScoreRequiredException;
import com.flowops.task.domain.exception.CannotReviewOwnWorkException;
import com.flowops.task.domain.exception.IllegalTransitionException;
import com.flowops.task.domain.model.Approval;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.PhaseTimer;
import com.flowops.task.domain.model.Task;
import com.flowops.task.domain.model.TaskId;
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

@Tag("TASK-APPROVE-01")
@ExtendWith(MockitoExtension.class)
class ApproveTaskServiceTest {
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

    @Mock
    private SaveApprovalPort saveApprovalPort;

    @Mock
    private NotifyTaskProgressPort notifyTaskProgressPort;

    private ApproveTaskService service;

    @BeforeEach
    void buildTheService() {
        service = new ApproveTaskService(reviews(), saveApprovalPort, notifyTaskProgressPort);
    }

    @Test
    void approvingMovesTheTaskAndHandsTheClockToTheApprover() {
        Task completed = completed();
        ionutIsTheReviewerOf(completed);
        theAssigneeIsKnown();

        assertThat(service.execute(new ApproveTaskCommand(completed.id(), 4, "Clear and on time."))
                        .task()
                        .state())
                .isEqualTo(TaskState.APPROVED);

        TaskMove move = theMoveWritten();
        assertThat(move.closedPhase().orElseThrow().kind()).isEqualTo(PhaseKind.REVIEW);
        assertThat(move.openedPhase().orElseThrow().kind()).isEqualTo(PhaseKind.APPROVAL);
        assertThat(move.event().action()).isEqualTo(TaskAction.TASK_APPROVED);
        assertThat(move.transition().actor()).isEqualTo(IONUT);
    }

    @Test
    void theJudgementIsStoredWithItsScoreItsCommentAndWhoMadeIt() {
        Task completed = completed();
        ionutIsTheReviewerOf(completed);
        theAssigneeIsKnown();

        service.execute(new ApproveTaskCommand(completed.id(), 4, "Clear and on time."));

        ArgumentCaptor<Approval> stored = ArgumentCaptor.forClass(Approval.class);
        verify(saveApprovalPort).save(stored.capture());
        assertThat(stored.getValue().task()).isEqualTo(completed.id());
        assertThat(stored.getValue().score().value()).isEqualTo(4);
        assertThat(stored.getValue().writtenComment()).contains("Clear and on time.");
        assertThat(stored.getValue().reviewer()).isEqualTo(IONUT);
        assertThat(stored.getValue().decidedAt()).isEqualTo(NOW);
    }

    @Test
    void theAssigneeIsToldTheirWorkWasAccepted() {
        Task completed = completed();
        ionutIsTheReviewerOf(completed);
        theAssigneeIsKnown();

        service.execute(new ApproveTaskCommand(completed.id(), 5, null));

        verify(notifyTaskProgressPort).approved(completed.id(), ANDREI);
    }

    @Test
    void nobodyApprovesTheirOwnWorkHoweverMuchAuthorityTheyHold() {
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
                .completed();
        signedInAs(IONUT);
        theCallerSeesEverything();
        theTaskIs(mine);
        itsOpenPhaseIs(PhaseTimer.opened(mine.id(), PhaseKind.REVIEW, CREATED_AT));

        assertThatThrownBy(() -> service.execute(new ApproveTaskCommand(mine.id(), 5, null)))
                .isInstanceOf(CannotReviewOwnWorkException.class);

        verifyNoInteractions(taskWriter, saveApprovalPort, notifyTaskProgressPort);
    }

    @Test
    void aManagerMayNotApproveWorkOutsideTheirOwnSubtree() {
        Task completed = completed();
        signedInAs(IONUT);
        when(callerPermissionsPort.callerHolds("TASK_VIEW_ANY")).thenReturn(false);
        when(reportingLinePort.subtreeOf(IONUT)).thenReturn(Set.of(MARIA));
        theTaskIs(completed);

        assertThatThrownBy(() -> service.execute(new ApproveTaskCommand(completed.id(), 4, null)))
                .isInstanceOf(TaskOutOfScopeException.class);

        verifyNoInteractions(taskWriter, saveApprovalPort, notifyTaskProgressPort);
    }

    @Test
    void theOwnerReachesWorkAnywhereInTheWorkspace() {
        Task completed = completed();
        signedInAs(MARIA);
        theCallerSeesEverything();
        theTaskIs(completed);
        itsOpenPhaseIs(PhaseTimer.opened(completed.id(), PhaseKind.REVIEW, CREATED_AT));
        theAssigneeIsKnown();

        assertThat(service.execute(new ApproveTaskCommand(completed.id(), 3, null))
                        .task()
                        .state())
                .isEqualTo(TaskState.APPROVED);
    }

    @Test
    void approvingWithNoScoreIsRefusedAndWritesNothing() {
        Task completed = completed();
        ionutIsTheReviewerOf(completed);

        assertThatThrownBy(() -> service.execute(new ApproveTaskCommand(completed.id(), null, "Fine.")))
                .isInstanceOf(ApprovalScoreRequiredException.class);

        verifyNoInteractions(taskWriter, saveApprovalPort, notifyTaskProgressPort);
    }

    @Test
    void approvingWithAScoreOutsideOneToFiveIsRefusedAndWritesNothing() {
        Task completed = completed();
        ionutIsTheReviewerOf(completed);

        assertThatThrownBy(() -> service.execute(new ApproveTaskCommand(completed.id(), 9, null)))
                .isInstanceOf(ApprovalScoreOutOfRangeException.class);

        verifyNoInteractions(taskWriter, saveApprovalPort, notifyTaskProgressPort);
    }

    @Test
    void approvingWorkThatWasNeverSubmittedIsRefusedAndNamesItsState() {
        Task underway = task().accepted().started();
        signedInAs(IONUT);
        theCallerSeesEverything();
        theTaskIs(underway);
        itsOpenPhaseIs(PhaseTimer.opened(underway.id(), PhaseKind.ACTIVE, CREATED_AT));

        assertThatThrownBy(() -> service.execute(new ApproveTaskCommand(underway.id(), 4, null)))
                .isInstanceOf(IllegalTransitionException.class)
                .satisfies(failure -> assertThat(((IllegalTransitionException) failure).from())
                        .isEqualTo(TaskState.IN_PROGRESS));

        verify(taskWriter, never()).writeMove(any());
    }

    @Test
    void aTaskThatDoesNotExistIsRefused() {
        TaskId missing = TaskId.generate();
        signedInAs(IONUT);
        when(loadTaskPort.lockForTransition(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(new ApproveTaskCommand(missing, 4, null)))
                .isInstanceOf(TaskNotFoundException.class);
    }

    private void ionutIsTheReviewerOf(Task task) {
        signedInAs(IONUT);
        theCallerSeesEverything();
        theTaskIs(task);
        itsOpenPhaseIs(PhaseTimer.opened(task.id(), PhaseKind.REVIEW, CREATED_AT));
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

    private static Task completed() {
        return task().accepted().started().completed();
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
