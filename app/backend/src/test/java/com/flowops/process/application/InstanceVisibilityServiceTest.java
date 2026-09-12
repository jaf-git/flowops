package com.flowops.process.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowops.process.application.shared.port.LoadInstancePort;
import com.flowops.process.application.shared.port.ReportingLinePort;
import com.flowops.process.application.streamvisibility.InstanceVisibilityService;
import com.flowops.process.domain.model.InstanceId;
import com.flowops.process.domain.model.PersonId;
import com.flowops.process.domain.model.ProcessInstance;
import com.flowops.process.domain.model.ProcessTemplate;
import com.flowops.process.domain.model.ProcessTemplate.StepDraft;
import com.flowops.process.domain.model.TaskRef;
import com.flowops.process.domain.model.TemplateId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("CANVAS-VIEW-PROCESS-01")
class InstanceVisibilityServiceTest {
    private static final PersonId MARIA = PersonId.of(UUID.randomUUID());
    private static final PersonId IOANA = PersonId.of(UUID.randomUUID());
    private static final PersonId DAN = PersonId.of(UUID.randomUUID());
    private static final Instant NOON = Instant.parse("2026-08-15T09:00:00Z");

    private static final Set<String> AN_EMPLOYEE = Set.of("PROCESS_VIEW_OWN");
    private static final Set<String> A_MANAGER = Set.of("PROCESS_VIEW_OWN", "PROCESS_VIEW_SUBTREE");
    private static final Set<String> THE_OWNER = Set.of("PROCESS_VIEW_OWN", "PROCESS_VIEW_SUBTREE", "PROCESS_VIEW_ANY");

    private LoadInstancePort instances;
    private ReportingLinePort tree;
    private InstanceVisibilityService visibility;
    private ProcessInstance run;

    private final com.flowops.process.domain.TemplateWorkbench library =
            new com.flowops.process.domain.TemplateWorkbench();

    @BeforeEach
    void setUp() {
        instances = mock(LoadInstancePort.class);
        tree = mock(ReportingLinePort.class);
        visibility = new InstanceVisibilityService(instances, tree);

        ProcessTemplate template = ProcessTemplate.authored(
                TemplateId.of(UUID.randomUUID()),
                "Lansare produs",
                "Cum procedăm",
                List.of(StepDraft.added(library.work("Pregătește materialele"), 4)),
                IOANA,
                NOON);
        run = ProcessInstance.cutFrom(
                InstanceId.of(UUID.randomUUID()), template, library.resolved(), "Lansare — august", IOANA, IOANA, NOON);
        when(instances.findById(run.id())).thenReturn(Optional.of(run));
        when(tree.subtreeOf(any())).thenReturn(Set.of());
    }

    @Test
    void theRunsOwnerSeesIt() {
        assertThat(visibility.mayView(IOANA.value(), AN_EMPLOYEE, run.id().value()))
                .isTrue();
    }

    @Test
    void somebodyOutsideTheRunDoesNotSeeIt() {
        assertThat(visibility.mayView(DAN.value(), AN_EMPLOYEE, run.id().value()))
                .isFalse();
    }

    @Test
    void amanagerSeesItWhileTheOwnerIsInTheirSubtreeAndStopsWhenTheyAreNot() {
        when(tree.subtreeOf(MARIA)).thenReturn(Set.of(IOANA));
        assertThat(visibility.mayView(MARIA.value(), A_MANAGER, run.id().value()))
                .as("Ioana reports to Maria at 13:59")
                .isTrue();

        when(tree.subtreeOf(MARIA)).thenReturn(Set.of());
        assertThat(visibility.mayView(MARIA.value(), A_MANAGER, run.id().value()))
                .as("Ioana was moved elsewhere at 14:00, and the stream must notice mid-connection")
                .isFalse();
    }

    @Test
    void thesubtreeIsNotEvenAskedForWithoutThePermissionForIt() {
        when(tree.subtreeOf(MARIA)).thenReturn(Set.of(IOANA));

        assertThat(visibility.mayView(MARIA.value(), AN_EMPLOYEE, run.id().value()))
                .isFalse();
        verify(tree, never()).subtreeOf(any());
    }

    @Test
    void seeingEverythingSeesThisToo() {
        assertThat(visibility.mayView(DAN.value(), THE_OWNER, run.id().value())).isTrue();
    }

    @Test
    void anInstanceThatDoesNotExistAnswersLikeOneOutOfScope() {
        UUID absent = UUID.randomUUID();
        when(instances.findById(InstanceId.of(absent))).thenReturn(Optional.empty());

        assertThat(visibility.mayView(IOANA.value(), THE_OWNER, absent)).isFalse();
    }

    @Test
    void holdingNoViewPermissionAtAllSeesNothing() {
        assertThat(visibility.mayView(IOANA.value(), Set.of(), run.id().value()))
                .isFalse();
        verify(instances, never()).findById(any());
    }

    @Test
    void aTaskThatBelongsToNoRunNamesNone() {
        UUID loose = UUID.randomUUID();
        when(instances.findByTask(TaskRef.of(loose))).thenReturn(Optional.empty());

        assertThat(visibility.instanceOf(loose)).isEmpty();
    }

    @Test
    void aTaskCutFromAStepNamesTheRunItCameFrom() {
        UUID fromAStep = UUID.randomUUID();
        when(instances.findByTask(TaskRef.of(fromAStep))).thenReturn(Optional.of(run));

        assertThat(visibility.instanceOf(fromAStep)).contains(run.id().value());
    }
}
