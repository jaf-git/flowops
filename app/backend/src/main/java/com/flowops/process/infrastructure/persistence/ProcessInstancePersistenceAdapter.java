package com.flowops.process.infrastructure.persistence;

import com.flowops.process.application.shared.port.LoadInstancePort;
import com.flowops.process.application.shared.port.SaveInstancePort;
import com.flowops.process.application.shared.port.WorkspacePort;
import com.flowops.process.domain.enums.InstanceState;
import com.flowops.process.domain.enums.StepCondition;
import com.flowops.process.domain.model.Applicability;
import com.flowops.process.domain.model.InstanceId;
import com.flowops.process.domain.model.InstanceStep;
import com.flowops.process.domain.model.PersonId;
import com.flowops.process.domain.model.ProcessInstance;
import com.flowops.process.domain.model.StepDependency;
import com.flowops.process.domain.model.StepId;
import com.flowops.process.domain.model.TaskRef;
import com.flowops.process.domain.model.TaskTemplateRef;
import com.flowops.process.domain.model.TemplateId;
import com.flowops.process.infrastructure.persistence.entity.InstanceStepDependencyJpaEntity;
import com.flowops.process.infrastructure.persistence.entity.InstanceStepJpaEntity;
import com.flowops.process.infrastructure.persistence.entity.ProcessInstanceJpaEntity;
import com.flowops.process.infrastructure.persistence.repository.InstanceStepDependencyJpaRepository;
import com.flowops.process.infrastructure.persistence.repository.InstanceStepJpaRepository;
import com.flowops.process.infrastructure.persistence.repository.ProcessInstanceJpaRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ProcessInstancePersistenceAdapter implements SaveInstancePort, LoadInstancePort {
    private final ProcessInstanceJpaRepository instances;
    private final InstanceStepJpaRepository steps;
    private final InstanceStepDependencyJpaRepository edges;
    private final WorkspacePort workspacePort;

    public ProcessInstancePersistenceAdapter(
            ProcessInstanceJpaRepository instances,
            InstanceStepJpaRepository steps,
            InstanceStepDependencyJpaRepository edges,
            WorkspacePort workspacePort) {
        this.instances = instances;
        this.steps = steps;
        this.edges = edges;
        this.workspacePort = workspacePort;
    }

    @Override
    public void create(ProcessInstance instance) {
        instances.save(new ProcessInstanceJpaEntity(
                instance.id().value(),
                workspacePort.currentWorkspaceId(),
                instance.name(),
                instance.template() == null ? null : instance.template().value(),
                instance.owner().value(),
                instance.startedBy().value(),
                instance.state().name(),
                instance.startedAt(),
                instance.completedAt()));
        for (InstanceStep step : instance.steps()) {
            steps.save(rowFor(instance.id(), step));
        }
        for (StepDependency edge : instance.dependencies()) {
            edges.save(new InstanceStepDependencyJpaEntity(
                    instance.id().value(),
                    edge.dependent().value(),
                    edge.dependsOn().value()));
        }
    }

    @Override
    public void addStep(InstanceId instance, InstanceStep step) {
        steps.save(rowFor(instance, step));
    }

    @Override
    public void removeStep(InstanceId instance, StepId step) {
        edges.deleteByDependentStepIdOrDependsOnStepId(step.value(), step.value());
        steps.deleteById(step.value());
    }

    @Override
    public void replaceEdges(InstanceId instance, List<StepDependency> drawn) {
        edges.deleteByInstanceId(instance.value());
        for (StepDependency edge : drawn) {
            edges.save(new InstanceStepDependencyJpaEntity(
                    instance.value(), edge.dependent().value(), edge.dependsOn().value()));
        }
    }

    @Override
    public void updatePositions(ProcessInstance instance) {
        for (InstanceStep step : instance.steps()) {
            InstanceStepJpaEntity stored = steps.findById(step.id().value()).orElseThrow();
            stored.moveTo(step.position());
            steps.save(stored);
        }
    }

    private static InstanceStepJpaEntity rowFor(InstanceId instance, InstanceStep step) {
        return new InstanceStepJpaEntity(
                step.id().value(),
                instance.value(),
                step.definitionId() == null ? null : step.definitionId().value(),
                step.taskTemplateId() == null ? null : step.taskTemplateId().value(),
                step.title(),
                step.description(),
                step.expectedDurationHours(),
                step.position(),
                step.condition().name(),
                step.task() == null ? null : step.task().value(),
                step.assignee() == null ? null : step.assignee().value(),
                step.applicability().optional(),
                step.applicability().conditionNote(),
                step.reachableAt(),
                step.closedAt(),
                step.skippedAt());
    }

    @Override
    public void updateAll(ProcessInstance instance) {
        ProcessInstanceJpaEntity stored =
                instances.findById(instance.id().value()).orElseThrow();
        stored.moveTo(instance.state().name(), instance.completedAt());
        instances.save(stored);
        for (InstanceStep step : instance.steps()) {
            updateStep(instance.id(), step);
        }
    }

    @Override
    public void archive(InstanceId instance, Instant archivedAt) {
        ProcessInstanceJpaEntity stored = instances.findById(instance.value()).orElseThrow();
        stored.archivedAt(archivedAt);
        instances.save(stored);
    }

    @Override
    public void restore(InstanceId instance) {
        ProcessInstanceJpaEntity stored = instances.findById(instance.value()).orElseThrow();
        stored.archivedAt(null);
        instances.save(stored);
    }

    @Override
    public void abandon(ProcessInstance instance) {
        ProcessInstanceJpaEntity stored =
                instances.findById(instance.id().value()).orElseThrow();
        ProcessInstance.Abandonment ending = instance.abandonment()
                .orElseThrow(() -> new IllegalStateException(
                        "instance " + instance.id().value() + " reached the abandon write without a reason"));
        stored.abandon(instance.state().name(), ending.at(), ending.reason());
        instances.save(stored);
    }

    @Override
    public void closeEarly(ProcessInstance instance) {
        ProcessInstanceJpaEntity stored =
                instances.findById(instance.id().value()).orElseThrow();
        String note = instance.closureNote()
                .orElseThrow(() -> new IllegalStateException(
                        "instance " + instance.id().value() + " reached the closure write without a note"));
        stored.closeEarly(instance.state().name(), instance.completedAt(), note);
        instances.save(stored);
    }

    @Override
    public Optional<ProcessInstance> findByTask(TaskRef task) {
        return steps.findByTaskId(task.value())
                .map(step -> rehydrate(instances.findById(step.getInstanceId()).orElseThrow()));
    }

    @Override
    public List<ProcessInstance> findRunning() {
        return rehydrateAll(instances.findByStateOrderByStartedAtDesc(InstanceState.RUNNING.name()));
    }

    @Override
    public void updateStep(InstanceId instance, InstanceStep step) {
        InstanceStepJpaEntity stored = steps.findById(step.id().value()).orElseThrow();
        stored.moveTo(
                step.condition().name(),
                step.task() == null ? null : step.task().value(),
                step.assignee() == null ? null : step.assignee().value(),
                step.reachableAt(),
                step.closedAt(),
                step.skippedAt());
        steps.save(stored);
    }

    @Override
    public Optional<ProcessInstance> findById(InstanceId id) {
        return instances.findById(id.value()).map(this::rehydrate);
    }

    @Override
    public List<ProcessInstance> findInvolving(Set<PersonId> people) {
        List<UUID> ids = new ArrayList<>();
        for (PersonId person : people) {
            ids.add(person.value());
        }
        return rehydrateAll(instances.findInvolving(ids));
    }

    @Override
    public List<ProcessInstance> findAll() {
        return rehydrateAll(instances.findAllByOrderByStartedAtDesc());
    }

    @Override
    public List<ProcessInstance> findOnTheBoard() {
        return rehydrateAll(instances.findByArchivedAtIsNullOrderByStartedAtDesc());
    }

    @Override
    public List<ProcessInstance> findOnTheBoardInvolving(Set<PersonId> people) {
        List<UUID> ids = new ArrayList<>();
        for (PersonId person : people) {
            ids.add(person.value());
        }
        return rehydrateAll(instances.findOnTheBoardInvolving(ids));
    }

    private List<ProcessInstance> rehydrateAll(Collection<ProcessInstanceJpaEntity> stored) {
        List<ProcessInstance> all = new ArrayList<>();
        for (ProcessInstanceJpaEntity row : stored) {
            all.add(rehydrate(row));
        }
        return all;
    }

    private ProcessInstance rehydrate(ProcessInstanceJpaEntity stored) {
        List<InstanceStep> copied = new ArrayList<>();
        for (InstanceStepJpaEntity step : steps.findByInstanceIdOrderByPosition(stored.getId())) {
            copied.add(new InstanceStep(
                    StepId.of(step.getId()),
                    step.getDefinitionId() == null ? null : StepId.of(step.getDefinitionId()),
                    step.getTaskTemplateId() == null ? null : TaskTemplateRef.of(step.getTaskTemplateId()),
                    step.getTitle(),
                    step.getDescription(),
                    step.getExpectedDurationHours(),
                    step.getPosition(),
                    new Applicability(step.isOptional(), step.getConditionNote()),
                    StepCondition.valueOf(step.getCondition()),
                    step.getTaskId() == null ? null : TaskRef.of(step.getTaskId()),
                    step.getAssigneeUserId() == null ? null : PersonId.of(step.getAssigneeUserId()),
                    step.getReachableAt(),
                    step.getClosedAt(),
                    step.getSkippedAt()));
        }
        List<StepDependency> drawn = new ArrayList<>();
        for (InstanceStepDependencyJpaEntity edge : edges.findByInstanceId(stored.getId())) {
            drawn.add(new StepDependency(StepId.of(edge.getDependentStepId()), StepId.of(edge.getDependsOnStepId())));
        }
        return ProcessInstance.existing(
                InstanceId.of(stored.getId()),
                stored.getName(),
                stored.getTemplateId() == null ? null : TemplateId.of(stored.getTemplateId()),
                PersonId.of(stored.getProcessOwnerUserId()),
                PersonId.of(stored.getStartedByUserId()),
                InstanceState.valueOf(stored.getState()),
                stored.getStartedAt(),
                stored.getCompletedAt(),
                copied,
                drawn,
                stored.getAbandonedAt() == null
                        ? null
                        : new ProcessInstance.Abandonment(stored.getAbandonedAt(), stored.getAbandonedReason()),
                stored.getClosureNote());
    }
}
