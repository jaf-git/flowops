package com.flowops.process.api.mapper;

import com.flowops.process.api.dto.AddTaskRequest;
import com.flowops.process.api.dto.BottleneckResponse;
import com.flowops.process.api.dto.InstanceEdgeResponse;
import com.flowops.process.api.dto.InstanceListResponse;
import com.flowops.process.api.dto.InstanceResponse;
import com.flowops.process.api.dto.InstanceStepResponse;
import com.flowops.process.api.dto.InstanceSummaryResponse;
import com.flowops.process.api.dto.ProgressResponse;
import com.flowops.process.api.dto.StepPhaseResponse;
import com.flowops.process.application.addtask.AddTaskToInstanceCommand;
import com.flowops.process.application.shared.exception.AddTaskRequestNotOneOfTwoException;
import com.flowops.process.application.shared.port.LoadPersonPort;
import com.flowops.process.application.shared.port.TaskStatePort;
import com.flowops.process.application.viewinstance.InstanceView;
import com.flowops.process.domain.enums.InstanceState;
import com.flowops.process.domain.model.InstanceId;
import com.flowops.process.domain.model.InstanceStep;
import com.flowops.process.domain.model.PersonId;
import com.flowops.process.domain.model.ProcessInstance;
import com.flowops.process.domain.model.StepDependency;
import com.flowops.process.domain.model.StepId;
import com.flowops.process.domain.model.TaskRef;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ProcessInstanceDtoMapper {
    private final Clock clock;
    private final LoadPersonPort loadPersonPort;

    public ProcessInstanceDtoMapper(Clock clock, LoadPersonPort loadPersonPort) {
        this.loadPersonPort = loadPersonPort;
        this.clock = clock;
    }

    public InstanceResponse toResponse(InstanceView view) {
        return toResponse(view.instance(), view.progress(), view.templateName());
    }

    public AddTaskToInstanceCommand toAddCommand(InstanceId instance, AddTaskRequest request) {
        boolean attaching = request.taskId() != null;
        boolean creating = request.title() != null && !request.title().isBlank();
        if (attaching == creating) {
            throw new AddTaskRequestNotOneOfTwoException();
        }

        List<UUID> waitsFor =
                request.dependsOnStepIds() == null ? List.<UUID>of() : List.copyOf(request.dependsOnStepIds());

        return new AddTaskToInstanceCommand(
                instance.value(),
                attaching ? request.taskId() : null,
                attaching
                        ? null
                        : new AddTaskToInstanceCommand.NewTask(
                                request.title(),
                                request.description(),
                                request.assigneeId(),
                                request.deadline(),
                                request.priority() == null ? "NORMAL" : request.priority()),
                waitsFor);
    }

    public InstanceResponse toResponse(ProcessInstance instance) {
        return toResponse(instance, Map.of(), null);
    }

    private InstanceResponse toResponse(
            ProcessInstance instance, Map<TaskRef, TaskStatePort.TaskProgress> progress, String templateName) {
        Map<PersonId, String> names = namesBehind(progress);
        List<InstanceStepResponse> steps = new ArrayList<>();
        for (InstanceStep step : instance.steps()) {
            steps.add(new InstanceStepResponse(
                    step.id().value(),
                    step.definitionId() == null ? null : step.definitionId().value(),
                    titleOf(progress, step),
                    descriptionOf(progress, step),
                    step.expectedDurationHours(),
                    step.position(),
                    step.condition().name(),
                    step.task() == null ? null : step.task().value(),
                    step.task() == null,
                    step.applicability().optional(),
                    step.applicability().conditionNote(),
                    step.wasSkipped(),
                    dependenciesOf(instance, step.id()),
                    stateOf(progress, step),
                    assigneeOf(progress, step),
                    assigneeNameOf(progress, step, names),
                    deadlineOf(progress, step),
                    atRiskOf(progress, step),
                    phasesOf(progress, step),
                    blockedReasonOf(progress, step)));
        }

        List<UUID> awaiting = new ArrayList<>();
        for (StepId step : instance.awaitingAssignment()) {
            awaiting.add(step.value());
        }

        BottleneckResponse bottleneck = instance.bottleneck(clock.instant(), waitingByStep(instance, progress))
                .map(held -> new BottleneckResponse(
                        held.step().value(), held.waitedFor().toMinutes()))
                .orElse(null);

        List<InstanceEdgeResponse> edges = new ArrayList<>();
        for (StepDependency dependency : instance.dependencies()) {
            edges.add(new InstanceEdgeResponse(
                    dependency.dependsOn().value(), dependency.dependent().value(), isClosed(instance, dependency)));
        }

        return new InstanceResponse(
                instance.id().value(),
                instance.name(),
                instance.state().name(),
                instance.template() == null ? null : instance.template().value(),
                templateName,
                instance.owner().value(),
                instance.startedAt(),
                instance.completedAt(),
                new ProgressResponse(
                        instance.progress().closed(), instance.progress().total()),
                steps,
                edges,
                awaiting,
                bottleneck,
                instance.totalDuration().map(java.time.Duration::toMinutes).orElse(null),
                instance.abandonment().map(ProcessInstance.Abandonment::at).orElse(null),
                instance.abandonment().map(ProcessInstance.Abandonment::reason).orElse(null),
                instance.closureNote().orElse(null),
                needingAttention(instance, progress));
    }

    private List<UUID> needingAttention(ProcessInstance instance, Map<TaskRef, TaskStatePort.TaskProgress> progress) {
        if (instance.state() == InstanceState.RUNNING) {
            return List.of();
        }
        List<UUID> survivors = new ArrayList<>();
        for (InstanceStep step : instance.steps()) {
            if (step.task() == null) {
                continue;
            }
            TaskStatePort.TaskProgress task = progress.get(step.task());

            if (task == null || (!"CLOSED".equals(task.state()) && !"APPROVED".equals(task.state()))) {
                survivors.add(step.id().value());
            }
        }
        return survivors;
    }

    private Map<PersonId, String> namesBehind(Map<TaskRef, TaskStatePort.TaskProgress> progress) {
        List<PersonId> holders = new ArrayList<>();
        for (TaskStatePort.TaskProgress task : progress.values()) {
            if (task.assignee() != null) {
                holders.add(PersonId.of(task.assignee()));
            }
        }
        Map<PersonId, String> names = new HashMap<>();
        for (LoadPersonPort.Person person : loadPersonPort.describeAll(holders)) {
            names.put(person.id(), person.displayName());
        }
        return names;
    }

    private static boolean isClosed(ProcessInstance instance, StepDependency dependency) {
        for (InstanceStep step : instance.steps()) {
            if (step.id().equals(dependency.dependsOn())) {
                return step.isClosed();
            }
        }
        return false;
    }

    private static TaskStatePort.TaskProgress taskOf(
            Map<TaskRef, TaskStatePort.TaskProgress> progress, InstanceStep step) {
        return step.task() == null ? null : progress.get(step.task());
    }

    private static String titleOf(Map<TaskRef, TaskStatePort.TaskProgress> progress, InstanceStep step) {
        TaskStatePort.TaskProgress found = taskOf(progress, step);
        return found == null ? step.title() : found.title();
    }

    private static String descriptionOf(Map<TaskRef, TaskStatePort.TaskProgress> progress, InstanceStep step) {
        TaskStatePort.TaskProgress found = taskOf(progress, step);
        return found == null ? step.description() : found.description();
    }

    private static UUID assigneeOf(Map<TaskRef, TaskStatePort.TaskProgress> progress, InstanceStep step) {
        TaskStatePort.TaskProgress found = taskOf(progress, step);
        return found == null ? null : found.assignee();
    }

    private static String assigneeNameOf(
            Map<TaskRef, TaskStatePort.TaskProgress> progress, InstanceStep step, Map<PersonId, String> names) {
        UUID holder = assigneeOf(progress, step);
        return holder == null ? null : names.getOrDefault(PersonId.of(holder), "");
    }

    private static Instant deadlineOf(Map<TaskRef, TaskStatePort.TaskProgress> progress, InstanceStep step) {
        TaskStatePort.TaskProgress found = taskOf(progress, step);
        return found == null ? null : found.deadline();
    }

    private static boolean atRiskOf(Map<TaskRef, TaskStatePort.TaskProgress> progress, InstanceStep step) {
        TaskStatePort.TaskProgress found = taskOf(progress, step);
        return found != null && found.atRisk();
    }

    private static List<StepPhaseResponse> phasesOf(
            Map<TaskRef, TaskStatePort.TaskProgress> progress, InstanceStep step) {
        TaskStatePort.TaskProgress found = taskOf(progress, step);
        List<StepPhaseResponse> phases = new ArrayList<>();
        if (found != null) {
            for (TaskStatePort.Phase phase : found.phases()) {
                phases.add(new StepPhaseResponse(phase.kind(), phase.seconds()));
            }
        }
        return phases;
    }

    private static String stateOf(Map<TaskRef, TaskStatePort.TaskProgress> progress, InstanceStep step) {
        TaskStatePort.TaskProgress found = step.task() == null ? null : progress.get(step.task());
        return found == null ? null : found.state();
    }

    private static String blockedReasonOf(Map<TaskRef, TaskStatePort.TaskProgress> progress, InstanceStep step) {
        TaskStatePort.TaskProgress found = step.task() == null ? null : progress.get(step.task());
        return found == null ? null : found.blockedReason();
    }

    private static Map<StepId, Duration> waitingByStep(
            ProcessInstance instance, Map<TaskRef, TaskStatePort.TaskProgress> progress) {
        Map<StepId, Duration> waiting = new HashMap<>();
        for (InstanceStep step : instance.steps()) {
            TaskStatePort.TaskProgress found = step.task() == null ? null : progress.get(step.task());
            if (found != null) {
                waiting.put(step.id(), found.waiting());
            }
        }
        return waiting;
    }

    public InstanceListResponse toList(List<InstanceView> views) {
        List<InstanceSummaryResponse> summaries = new ArrayList<>();
        for (InstanceView view : views) {
            ProcessInstance instance = view.instance();
            summaries.add(new InstanceSummaryResponse(
                    instance.id().value(),
                    instance.name(),
                    view.templateName(),
                    instance.state().name(),
                    new ProgressResponse(
                            instance.progress().closed(), instance.progress().total()),
                    instance.awaitingAssignment().size()));
        }
        return new InstanceListResponse(summaries);
    }

    private List<UUID> dependenciesOf(ProcessInstance instance, StepId step) {
        List<UUID> waits = new ArrayList<>();
        for (StepDependency edge : instance.dependencies()) {
            if (edge.dependent().equals(step)) {
                waits.add(edge.dependsOn().value());
            }
        }
        return waits;
    }
}
