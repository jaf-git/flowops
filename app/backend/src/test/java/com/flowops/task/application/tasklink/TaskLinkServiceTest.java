package com.flowops.task.application.tasklink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowops.task.application.shared.TaskMaterialSupport;
import com.flowops.task.application.shared.exception.NotTheAssigneeException;
import com.flowops.task.application.shared.exception.TaskNotFoundException;
import com.flowops.task.application.shared.port.AppendTaskEventPort;
import com.flowops.task.application.shared.port.IdentifyCallerPort;
import com.flowops.task.application.shared.port.LoadTaskPort;
import com.flowops.task.application.shared.port.TaskMaterialPort;
import com.flowops.task.domain.enums.LinkRole;
import com.flowops.task.domain.enums.TaskPriority;
import com.flowops.task.domain.exception.LinkSchemeNotAllowedException;
import com.flowops.task.domain.model.LinkUrl;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.Task;
import com.flowops.task.domain.model.TaskLink;
import com.flowops.task.domain.model.TaskLinkId;
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

@Tag("TASK-LINK-01")
@ExtendWith(MockitoExtension.class)
class TaskLinkServiceTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-13T09:00:00Z");
    private static final Instant NOW = Instant.parse("2026-08-13T14:00:00Z");
    private static final PersonId IONUT = PersonId.of(UUID.randomUUID());
    private static final PersonId ANDREI = PersonId.of(UUID.randomUUID());
    private static final PersonId ELENA = PersonId.of(UUID.randomUUID());
    private static final String BRIEF = "https://drive.atelier.ro/brief-q3.pdf";

    @Mock
    private IdentifyCallerPort identifyCallerPort;

    @Mock
    private LoadTaskPort loadTaskPort;

    @Mock
    private TaskMaterialPort taskMaterialPort;

    @Mock
    private AppendTaskEventPort appendTaskEventPort;

    private TaskLinkService service;
    private Task task;

    @BeforeEach
    void buildTheService() {
        task = Task.given("Reconciliază registrul", null, ANDREI, IONUT, null, TaskPriority.NORMAL, CREATED_AT);
        service = new TaskLinkService(
                new TaskMaterialSupport(identifyCallerPort, loadTaskPort, Clock.fixed(NOW, ZoneOffset.UTC)),
                taskMaterialPort,
                appendTaskEventPort);
    }

    @Test
    void bothTheAssigneeAndThePersonWhoGaveTheWorkOutMayAttach() {
        callerIs(ANDREI);
        TaskLink theirs = service.attach(new AttachLinkCommand(task.id(), BRIEF, "Result", LinkRole.OUTPUT));
        assertThat(theirs.addedBy()).isEqualTo(ANDREI);

        callerIs(IONUT);
        TaskLink assigners = service.attach(new AttachLinkCommand(task.id(), BRIEF, "Brief for Q3", LinkRole.INPUT));
        assertThat(assigners.addedBy()).isEqualTo(IONUT);
        assertThat(assigners.addedAt()).isEqualTo(NOW);
    }

    @Test
    void somebodyWithNoPartInTheWorkMayNotAttach() {
        callerIs(ELENA);

        assertThatThrownBy(() -> service.attach(new AttachLinkCommand(task.id(), BRIEF, "Curious", LinkRole.REFERENCE)))
                .isInstanceOf(NotTheAssigneeException.class);

        verify(taskMaterialPort, never()).attach(any());
        verify(appendTaskEventPort, never()).append(any());
    }

    @Test
    void aDisallowedAddressIsRefusedBeforeAnythingIsStored() {
        callerIs(ANDREI);

        assertThatThrownBy(() -> service.attach(
                        new AttachLinkCommand(task.id(), "javascript:alert(1)", null, LinkRole.REFERENCE)))
                .isInstanceOf(LinkSchemeNotAllowedException.class);

        verify(taskMaterialPort, never()).attach(any());
        verify(appendTaskEventPort, never()).append(any());
    }

    @Test
    void detachingReadsTheLinkScopedByTaskAndThenRemovesThatOne() {
        callerIs(ANDREI);
        TaskLink link = TaskLink.attached(task.id(), LinkUrl.of(BRIEF), "Brief for Q3", LinkRole.INPUT, IONUT, NOW);
        when(taskMaterialPort.findLink(task.id(), link.id())).thenReturn(Optional.of(link));

        service.detach(new DetachLinkCommand(task.id(), link.id()));

        verify(taskMaterialPort).detach(task.id(), link.id());
        verify(appendTaskEventPort).append(any());
    }

    @Test
    void aLinkThisTaskDoesNotHaveIsRefusedAndNothingIsDetached() {
        callerIs(ANDREI);
        TaskLinkId elsewhere = TaskLinkId.generate();
        when(taskMaterialPort.findLink(task.id(), elsewhere)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detach(new DetachLinkCommand(task.id(), elsewhere)))
                .isInstanceOf(TaskNotFoundException.class);

        verify(taskMaterialPort, never()).detach(any(), any());
        verify(appendTaskEventPort, never()).append(any());
    }

    private void callerIs(PersonId person) {
        when(identifyCallerPort.currentCaller()).thenReturn(Optional.of(person));
        when(loadTaskPort.findById(task.id())).thenReturn(Optional.of(task));
    }
}
