package com.flowops.task.application.viewtasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowops.task.application.shared.AtRiskRule;
import com.flowops.task.application.shared.TaskAudienceRule;
import com.flowops.task.application.shared.port.CallerPermissionsPort;
import com.flowops.task.application.shared.port.IdentifyCallerPort;
import com.flowops.task.application.shared.port.LoadDeadlineProposalPort;
import com.flowops.task.application.shared.port.LoadPersonPort;
import com.flowops.task.application.shared.port.LoadTaskPort;
import com.flowops.task.application.shared.port.PhaseTimerPort;
import com.flowops.task.application.shared.port.ReportingLinePort;
import com.flowops.task.application.shared.port.TaskCategoryFilingsPort;
import com.flowops.task.domain.enums.PhaseKind;
import com.flowops.task.domain.enums.TaskPriority;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.PhaseTimer;
import com.flowops.task.domain.model.Task;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

@Tag("TASK-CREATE-01")
@ExtendWith(MockitoExtension.class)
class ViewTasksServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-10T09:00:00Z");
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
    private LoadDeadlineProposalPort loadDeadlineProposalPort;

    @Mock
    private TaskCategoryFilingsPort taskCategoryFilingsPort;

    private ViewTasksService service;

    @BeforeEach
    void buildTheService() {
        service = new ViewTasksService(
                identifyCallerPort,
                callerPermissionsPort,
                reportingLinePort,
                new TaskAudienceRule(callerPermissionsPort, reportingLinePort),
                loadTaskPort,
                phaseTimerPort,
                loadPersonPort,
                loadDeadlineProposalPort,
                taskCategoryFilingsPort,
                new AtRiskRule(() -> 24, Clock.fixed(NOW, ZoneOffset.UTC)));
    }

    @Test
    void somebodyWithTheirOwnViewOnlySeesTheirOwnWork() {
        signedInAs(ANDREI);
        holds(false, false);
        when(loadTaskPort.findByAssigneesOrCreator(Set.of(ANDREI), ANDREI)).thenReturn(List.of());

        service.execute();

        verify(loadTaskPort).findByAssigneesOrCreator(Set.of(ANDREI), ANDREI);
        verify(loadTaskPort, never()).findAll();
        verify(reportingLinePort, never()).subtreeOf(ANDREI);
    }

    @Test
    void aManagerAlsoSeesTheirSubtreeAndIsStillInTheirOwnScope() {
        signedInAs(IONUT);
        holds(true, false);
        when(reportingLinePort.subtreeOf(IONUT)).thenReturn(Set.of(ANDREI));
        when(loadTaskPort.findByAssigneesOrCreator(Set.of(IONUT, ANDREI), IONUT))
                .thenReturn(List.of());

        service.execute();

        verify(loadTaskPort).findByAssigneesOrCreator(Set.of(IONUT, ANDREI), IONUT);
    }

    @Test
    void theOwnerSeesEverythingWithoutAskingForASubtree() {
        signedInAs(IONUT);
        when(callerPermissionsPort.callerHolds("TASK_VIEW_ANY")).thenReturn(true);
        when(loadTaskPort.findAll()).thenReturn(List.of());

        service.execute();

        verify(loadTaskPort).findAll();
        verify(loadTaskPort, never()).findByAssigneesOrCreator(anyCollection(), any());
        verify(reportingLinePort, never()).subtreeOf(IONUT);
    }

    @Test
    void everyRowNamesThePhaseItsClockBelongsTo() {
        Task task = taskFor(ANDREI);
        signedInAs(ANDREI);
        holds(false, false);
        when(loadTaskPort.findByAssigneesOrCreator(Set.of(ANDREI), ANDREI)).thenReturn(List.of(task));
        when(phaseTimerPort.openPhasesOf(List.of(task.id())))
                .thenReturn(List.of(PhaseTimer.opened(task.id(), PhaseKind.WAIT, NOW)));
        when(loadPersonPort.describeAll(List.of(ANDREI)))
                .thenReturn(List.of(new LoadPersonPort.Person(ANDREI, "Andrei Munteanu", true)));

        ViewTasksResult.Row row = service.execute().tasks().getFirst();

        assertThat(row.openPhase()).isEqualTo(PhaseKind.WAIT);
        assertThat(row.phaseSince()).isEqualTo(NOW);
        assertThat(row.assigneeName()).isEqualTo("Andrei Munteanu");
        assertThat(row.mine()).isTrue();
    }

    @Test
    void workHeldByAnErasedPersonComesBackWithNoName() {
        Task task = taskFor(ANDREI);
        signedInAs(IONUT);
        holds(false, false);
        when(loadTaskPort.findByAssigneesOrCreator(Set.of(IONUT), IONUT)).thenReturn(List.of(task));
        when(phaseTimerPort.openPhasesOf(List.of(task.id())))
                .thenReturn(List.of(PhaseTimer.opened(task.id(), PhaseKind.WAIT, NOW)));
        when(loadPersonPort.describeAll(List.of(ANDREI))).thenReturn(List.of());

        ViewTasksResult.Row row = service.execute().tasks().getFirst();

        assertThat(row.assigneeName()).isEmpty();
        assertThat(row.mine()).as("it was never the caller's").isFalse();
    }

    private void signedInAs(PersonId person) {
        when(identifyCallerPort.currentCaller()).thenReturn(Optional.of(person));
    }

    private void holds(boolean subtree, boolean any) {
        when(callerPermissionsPort.callerHolds("TASK_VIEW_ANY")).thenReturn(any);
        when(callerPermissionsPort.callerHolds("TASK_VIEW_SUBTREE")).thenReturn(subtree);
    }

    private static Task taskFor(PersonId assignee) {
        return Task.given(
                "Draft the supplier review", null, assignee, IONUT, NOW.plusSeconds(86_400), TaskPriority.NORMAL, NOW);
    }
}
