package com.flowops.task.application.unblocktask;

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
import com.flowops.task.application.shared.exception.TaskNotFoundException;
import com.flowops.task.application.shared.port.IdentifyCallerPort;
import com.flowops.task.application.shared.port.LoadPersonPort;
import com.flowops.task.application.shared.port.LoadTaskPort;
import com.flowops.task.application.shared.port.PhaseTimerPort;
import com.flowops.task.domain.enums.PhaseKind;
import com.flowops.task.domain.enums.TaskAction;
import com.flowops.task.domain.enums.TaskPriority;
import com.flowops.task.domain.enums.TaskState;
import com.flowops.task.domain.exception.IllegalTransitionException;
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

@Tag("TASK-UNBLOCK-01")
@ExtendWith(MockitoExtension.class)
class UnblockTaskServiceTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-11T09:00:00Z");
    private static final Instant NOW = Instant.parse("2026-08-11T14:00:00Z");
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

    private UnblockTaskService service;

    @BeforeEach
    void buildTheService() {
        service = new UnblockTaskService(transitions());
    }

    @Test
    void unblockingOpensANewActiveIntervalRatherThanResumingTheBlockedOne() {
        Task blocked = newTask().accepted().started().blocked();
        PhaseTimer blockedPhase = PhaseTimer.opened(blocked.id(), PhaseKind.BLOCKED, CREATED_AT);
        signedInAs(ANDREI);
        theTaskIs(blocked);
        itsOpenPhaseIs(blockedPhase);
        theAssigneeIsKnown();

        assertThat(service.execute(new UnblockTaskCommand(blocked.id(), "They sent the figures"))
                        .task()
                        .state())
                .isEqualTo(TaskState.IN_PROGRESS);

        TaskMove move = theMoveWritten();
        assertThat(move.closedPhase().orElseThrow().id()).isEqualTo(blockedPhase.id());
        assertThat(move.closedPhase().orElseThrow().kind()).isEqualTo(PhaseKind.BLOCKED);
        assertThat(move.openedPhase().orElseThrow().kind()).isEqualTo(PhaseKind.ACTIVE);
        assertThat(move.openedPhase().orElseThrow().id())
                .as("total active time is the sum of the rows, so this is a second row")
                .isNotEqualTo(blockedPhase.id());
        assertThat(move.openedPhase().orElseThrow().startedAt())
                .as("the clock resumes when the person resumed")
                .isEqualTo(NOW);
        assertThat(move.transition().statedReason()).contains("They sent the figures");
        assertThat(move.event().action()).isEqualTo(TaskAction.TASK_UNBLOCKED);
    }

    @Test
    void unblockingWithNoResolutionNoteSucceeds() {
        Task blocked = newTask().accepted().started().blocked();
        signedInAs(ANDREI);
        theTaskIs(blocked);
        itsOpenPhaseIs(PhaseTimer.opened(blocked.id(), PhaseKind.BLOCKED, CREATED_AT));
        theAssigneeIsKnown();

        assertThat(service.execute(new UnblockTaskCommand(blocked.id(), null))
                        .task()
                        .state())
                .isEqualTo(TaskState.IN_PROGRESS);
        assertThat(theMoveWritten().transition().statedReason()).isEmpty();
    }

    @Test
    void nobodyButTheAssigneeMayUnblock() {
        Task blocked = newTask().accepted().started().blocked();
        signedInAs(MARIA);
        theTaskIs(blocked);

        assertThatThrownBy(() -> service.execute(new UnblockTaskCommand(blocked.id(), null)))
                .isInstanceOf(NotTheAssigneeException.class);

        verifyNoInteractions(taskWriter);
    }

    @Test
    void unblockingSomethingThatWasNeverBlockedIsRefusedAndNamesItsState() {
        Task underway = newTask().accepted().started();
        signedInAs(ANDREI);
        theTaskIs(underway);
        itsOpenPhaseIs(PhaseTimer.opened(underway.id(), PhaseKind.ACTIVE, CREATED_AT));

        assertThatThrownBy(() -> service.execute(new UnblockTaskCommand(underway.id(), null)))
                .isInstanceOf(IllegalTransitionException.class)
                .satisfies(failure -> assertThat(((IllegalTransitionException) failure).from())
                        .isEqualTo(TaskState.IN_PROGRESS));

        verify(taskWriter, never()).writeMove(any());
    }

    @Test
    void aTaskThatDoesNotExistIsRefused() {
        TaskId missing = TaskId.generate();
        signedInAs(ANDREI);
        when(loadTaskPort.lockForTransition(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(new UnblockTaskCommand(missing, null)))
                .isInstanceOf(TaskNotFoundException.class);
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

    private void theAssigneeIsKnown() {
        when(loadPersonPort.describe(ANDREI))
                .thenReturn(Optional.of(new LoadPersonPort.Person(ANDREI, "Andrei Munteanu", true)));
    }

    private TaskMove theMoveWritten() {
        ArgumentCaptor<TaskMove> written = ArgumentCaptor.forClass(TaskMove.class);
        verify(taskWriter).writeMove(written.capture());
        return written.getValue();
    }

    private static Task newTask() {
        return Task.given(
                "Draft the supplier review",
                null,
                ANDREI,
                MARIA,
                CREATED_AT.plusSeconds(86_400),
                TaskPriority.NORMAL,
                CREATED_AT);
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
}
