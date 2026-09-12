package com.flowops.task.application.viewreviewqueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowops.task.application.shared.AtRiskRule;
import com.flowops.task.application.shared.port.CallerPermissionsPort;
import com.flowops.task.application.shared.port.IdentifyCallerPort;
import com.flowops.task.application.shared.port.LoadDeadlineProposalPort;
import com.flowops.task.application.shared.port.LoadPersonPort;
import com.flowops.task.application.shared.port.LoadTaskPort;
import com.flowops.task.application.shared.port.PhaseTimerPort;
import com.flowops.task.application.shared.port.ReportingLinePort;
import com.flowops.task.application.shared.port.TaskCategoryFilingsPort;
import com.flowops.task.application.viewtasks.ViewTasksResult;
import com.flowops.task.domain.enums.PhaseKind;
import com.flowops.task.domain.enums.TaskPriority;
import com.flowops.task.domain.enums.TaskState;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.PhaseTimer;
import com.flowops.task.domain.model.Task;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("TASK-REVIEW-01")
@ExtendWith(MockitoExtension.class)
class ViewReviewQueueServiceTest {
    private static final Instant MONDAY = Instant.parse("2026-08-10T09:00:00Z");
    private static final Instant TUESDAY = Instant.parse("2026-08-11T09:00:00Z");
    private static final PersonId MARIA = PersonId.of(UUID.randomUUID());
    private static final PersonId IONUT = PersonId.of(UUID.randomUUID());
    private static final PersonId ANDREI = PersonId.of(UUID.randomUUID());
    private static final PersonId ELENA = PersonId.of(UUID.randomUUID());

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
    private LoadDeadlineProposalPort loadDeadlineProposalPort;

    @Mock
    private TaskCategoryFilingsPort taskCategoryFilingsPort;

    private ViewReviewQueueService service;

    @BeforeEach
    void buildTheService() {
        service = new ViewReviewQueueService(
                identifyCallerPort,
                callerPermissionsPort,
                reportingLinePort,
                loadTaskPort,
                phaseTimerPort,
                loadPersonPort,
                loadDeadlineProposalPort,
                taskCategoryFilingsPort,
                new AtRiskRule(() -> 24, Clock.systemUTC()));
    }

    @Test
    void theOwnerSeesEveryCompletedTaskInTheWorkspace() {
        Task andreis = completed(ANDREI, MONDAY);
        Task elenas = completed(ELENA, TUESDAY);
        signedInAs(MARIA);
        when(callerPermissionsPort.callerHolds("TASK_VIEW_ANY")).thenReturn(true);
        when(loadTaskPort.findAllCompleted()).thenReturn(List.of(andreis, elenas));
        theOpenPhasesAre(reviewPhase(andreis, MONDAY), reviewPhase(elenas, TUESDAY));
        theseArePeople(ANDREI, ELENA);

        assertThat(service.execute().tasks())
                .extracting(ViewTasksResult.Row::id)
                .containsExactly(andreis.id(), elenas.id());
    }

    @Test
    void aManagerSeesOnlyCompletedWorkInsideTheirOwnSubtree() {
        Task andreis = completed(ANDREI, MONDAY);
        signedInAs(IONUT);
        when(callerPermissionsPort.callerHolds("TASK_VIEW_ANY")).thenReturn(false);
        when(reportingLinePort.subtreeOf(IONUT)).thenReturn(Set.of(ANDREI));
        when(loadTaskPort.findCompletedByAssignees(Set.of(IONUT, ANDREI))).thenReturn(List.of(andreis));
        theOpenPhasesAre(reviewPhase(andreis, MONDAY));
        theseArePeople(ANDREI);

        assertThat(service.execute().tasks()).hasSize(1);
        verify(loadTaskPort).findCompletedByAssignees(Set.of(IONUT, ANDREI));
    }

    @Test
    void theScopeIsReadFromTheTreeEveryTimeRatherThanRemembered() {
        signedInAs(IONUT);
        when(callerPermissionsPort.callerHolds("TASK_VIEW_ANY")).thenReturn(false);
        when(reportingLinePort.subtreeOf(IONUT)).thenReturn(Set.of(ANDREI));
        when(loadTaskPort.findCompletedByAssignees(anyCollection())).thenReturn(List.of());

        service.execute();
        service.execute();

        verify(reportingLinePort, times(2)).subtreeOf(IONUT);
    }

    @Test
    void myOwnCompletedWorkAppearsInMyQueueAndIsMarkedAsMine() {
        Task mine = completed(IONUT, MONDAY);
        signedInAs(IONUT);
        when(callerPermissionsPort.callerHolds("TASK_VIEW_ANY")).thenReturn(false);
        when(reportingLinePort.subtreeOf(IONUT)).thenReturn(Set.of());
        when(loadTaskPort.findCompletedByAssignees(Set.of(IONUT))).thenReturn(List.of(mine));
        theOpenPhasesAre(reviewPhase(mine, MONDAY));
        theseArePeople(IONUT);

        assertThat(service.execute().tasks()).singleElement().satisfies(row -> assertThat(row.mine())
                .isTrue());
    }

    @Test
    void anEmptyQueueComesBackEmptyRatherThanAsEverything() {
        signedInAs(IONUT);
        when(callerPermissionsPort.callerHolds("TASK_VIEW_ANY")).thenReturn(false);
        when(reportingLinePort.subtreeOf(IONUT)).thenReturn(Set.of(ANDREI));
        when(loadTaskPort.findCompletedByAssignees(Set.of(IONUT, ANDREI))).thenReturn(List.of());

        assertThat(service.execute().tasks()).isEmpty();
    }

    @Test
    void eachRowCarriesThePhaseItIsInAndTheInstantThatPhaseBegan() {
        Task andreis = completed(ANDREI, MONDAY);
        signedInAs(MARIA);
        when(callerPermissionsPort.callerHolds("TASK_VIEW_ANY")).thenReturn(true);
        when(loadTaskPort.findAllCompleted()).thenReturn(List.of(andreis));
        theOpenPhasesAre(reviewPhase(andreis, MONDAY));
        theseArePeople(ANDREI);

        assertThat(service.execute().tasks()).singleElement().satisfies(row -> {
            assertThat(row.openPhase()).isEqualTo(PhaseKind.REVIEW);
            assertThat(row.phaseSince()).isEqualTo(MONDAY);
            assertThat(row.state()).isEqualTo(TaskState.COMPLETED);
        });
    }

    private void signedInAs(PersonId person) {
        when(identifyCallerPort.currentCaller()).thenReturn(Optional.of(person));
    }

    private void theOpenPhasesAre(PhaseTimer... phases) {
        when(phaseTimerPort.openPhasesOf(anyCollection())).thenReturn(List.of(phases));
    }

    private void theseArePeople(PersonId... people) {
        when(loadPersonPort.describeAll(anyCollection()))
                .thenReturn(List.of(people).stream()
                        .map(person -> new LoadPersonPort.Person(person, "Somebody", true))
                        .toList());
    }

    private static PhaseTimer reviewPhase(Task task, Instant since) {
        return PhaseTimer.opened(task.id(), PhaseKind.REVIEW, since);
    }

    private static Task completed(PersonId assignee, Instant createdAt) {
        return Task.given(
                        "Draft the supplier review",
                        null,
                        assignee,
                        MARIA,
                        createdAt.plusSeconds(86_400),
                        TaskPriority.NORMAL,
                        createdAt)
                .accepted()
                .started()
                .completed();
    }

    @Test
    void anErasedAssigneeLeavesAnEmptyNameRatherThanNothingAtAll() {
        Task andreis = completed(ANDREI, MONDAY);
        signedInAs(MARIA);
        when(callerPermissionsPort.callerHolds("TASK_VIEW_ANY")).thenReturn(true);
        when(loadTaskPort.findAllCompleted()).thenReturn(List.of(andreis));
        theOpenPhasesAre(reviewPhase(andreis, MONDAY));
        when(loadPersonPort.describeAll(anyCollection())).thenReturn(List.of());

        assertThat(service.execute().tasks()).singleElement().satisfies(row -> assertThat(row.assigneeName())
                .isEmpty());
    }
}
