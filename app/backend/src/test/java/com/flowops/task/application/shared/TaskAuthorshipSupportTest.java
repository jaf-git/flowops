package com.flowops.task.application.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.flowops.task.application.shared.exception.NotAuthenticatedException;
import com.flowops.task.application.shared.exception.NotTheCreatorException;
import com.flowops.task.application.shared.exception.TaskNotFoundException;
import com.flowops.task.application.shared.port.CallerPermissionsPort;
import com.flowops.task.application.shared.port.IdentifyCallerPort;
import com.flowops.task.application.shared.port.LoadTaskPort;
import com.flowops.task.application.shared.port.WorkspaceSettingsPort;
import com.flowops.task.domain.enums.TaskPriority;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.Task;
import com.flowops.task.domain.model.TaskId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("TASK-DECIDE-DEADLINE-01")
@Tag("TASK-EDIT-01")
@ExtendWith(MockitoExtension.class)
class TaskAuthorshipSupportTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-11T09:00:00Z");
    private static final Instant NOW = Instant.parse("2026-08-11T14:00:00Z");
    private static final Instant DEADLINE = Instant.parse("2026-08-20T17:00:00Z");
    private static final PersonId MARIA = PersonId.of(UUID.randomUUID());
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

    private TaskAuthorshipSupport authorship;

    @BeforeEach
    void buildTheSupport() {
        authorship = new TaskAuthorshipSupport(
                identifyCallerPort,
                callerPermissionsPort,
                loadTaskPort,
                workspaceSettingsPort,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void thePersonWhoGaveTheWorkOutMayDirectIt() {
        Task task = givenBy(IONUT);
        signedInAs(IONUT);
        theTaskIs(task);

        assertThat(authorship.claim(task.id()).actor()).isEqualTo(IONUT);
    }

    @Test
    void theOwnerMayDirectWorkSomebodyElseAssigned() {
        Task task = givenBy(IONUT);
        signedInAs(MARIA);
        theTaskIs(task);
        when(callerPermissionsPort.callerHolds("TASK_VIEW_ANY")).thenReturn(true);

        assertThat(authorship.claim(task.id()).actor()).isEqualTo(MARIA);
    }

    @Test
    void anotherManagerHoldingThePermissionCannotDirectWorkTheyDidNotAssign() {
        Task task = givenBy(IONUT);
        signedInAs(CRISTINA);
        theTaskIs(task);
        lenient().when(callerPermissionsPort.callerHolds("TASK_VIEW_ANY")).thenReturn(false);

        assertThatThrownBy(() -> authorship.claim(task.id())).isInstanceOf(NotTheCreatorException.class);
    }

    @Test
    void theAssigneeCannotMoveTheirOwnDeadline() {
        Task task = givenBy(IONUT);
        signedInAs(ANDREI);
        theTaskIs(task);
        lenient().when(callerPermissionsPort.callerHolds("TASK_VIEW_ANY")).thenReturn(false);

        assertThatThrownBy(() -> authorship.claim(task.id())).isInstanceOf(NotTheCreatorException.class);
    }

    @Test
    void aTaskThatIsNotThereIsRefusedBeforeAuthorship() {
        signedInAs(IONUT);
        TaskId missing = TaskId.generate();
        when(loadTaskPort.lockForTransition(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authorship.claim(missing)).isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void withNoSessionNothingIsClaimed() {
        when(identifyCallerPort.currentCaller()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authorship.claim(TaskId.generate())).isInstanceOf(NotAuthenticatedException.class);
    }

    @Test
    void directsAnswersForTheCreatorAndTheOwnerAndNobodyElse() {
        when(callerPermissionsPort.callerHolds("TASK_VIEW_ANY")).thenReturn(false);

        assertThat(authorship.directs(IONUT, IONUT)).isTrue();
        assertThat(authorship.directs(CRISTINA, IONUT)).isFalse();
    }

    private void signedInAs(PersonId person) {
        when(identifyCallerPort.currentCaller()).thenReturn(Optional.of(person));
    }

    private void theTaskIs(Task task) {
        when(loadTaskPort.lockForTransition(task.id())).thenReturn(Optional.of(task));
    }

    private static Task givenBy(PersonId creator) {
        return Task.given(
                "Rebuild the supplier list",
                "Before the audit",
                ANDREI,
                creator,
                DEADLINE,
                TaskPriority.NORMAL,
                CREATED_AT);
    }
}
