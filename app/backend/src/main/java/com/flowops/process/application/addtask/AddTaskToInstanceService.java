package com.flowops.process.application.addtask;

import com.flowops.process.application.shared.exception.InstanceNotFoundException;
import com.flowops.process.application.shared.exception.NotAuthenticatedException;
import com.flowops.process.application.shared.exception.TaskAlreadyInAProcessException;
import com.flowops.process.application.shared.exception.TaskNotAttachableException;
import com.flowops.process.application.shared.port.AppendProcessEventPort;
import com.flowops.process.application.shared.port.AttachableTasksPort;
import com.flowops.process.application.shared.port.CreateTaskPort;
import com.flowops.process.application.shared.port.IdentifyCallerPort;
import com.flowops.process.application.shared.port.LoadInstancePort;
import com.flowops.process.application.shared.port.SaveInstancePort;
import com.flowops.process.application.shared.port.TaskProvenancePort;
import com.flowops.process.domain.enums.ProcessAction;
import com.flowops.process.domain.event.ProcessEvent;
import com.flowops.process.domain.model.InstanceId;
import com.flowops.process.domain.model.InstanceStep;
import com.flowops.process.domain.model.PersonId;
import com.flowops.process.domain.model.ProcessInstance;
import com.flowops.process.domain.model.StepDependency;
import com.flowops.process.domain.model.StepId;
import com.flowops.process.domain.model.TaskRef;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AddTaskToInstanceService implements AddTaskToInstanceUseCase {
    private final IdentifyCallerPort identifyCallerPort;
    private final LoadInstancePort loadInstancePort;
    private final SaveInstancePort saveInstancePort;
    private final AttachableTasksPort attachableTasksPort;
    private final TaskProvenancePort taskProvenancePort;
    private final CreateTaskPort createTaskPort;
    private final AppendProcessEventPort appendProcessEventPort;
    private final Clock clock;

    public AddTaskToInstanceService(
            IdentifyCallerPort identifyCallerPort,
            LoadInstancePort loadInstancePort,
            SaveInstancePort saveInstancePort,
            AttachableTasksPort attachableTasksPort,
            TaskProvenancePort taskProvenancePort,
            CreateTaskPort createTaskPort,
            AppendProcessEventPort appendProcessEventPort,
            Clock clock) {
        this.identifyCallerPort = identifyCallerPort;
        this.loadInstancePort = loadInstancePort;
        this.saveInstancePort = saveInstancePort;
        this.attachableTasksPort = attachableTasksPort;
        this.taskProvenancePort = taskProvenancePort;
        this.createTaskPort = createTaskPort;
        this.appendProcessEventPort = appendProcessEventPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AddTaskToInstanceUseCase.Added execute(AddTaskToInstanceCommand command) {
        Instant now = clock.instant();
        PersonId caller = identifyCallerPort
                .currentCaller()
                .orElseThrow(() -> new NotAuthenticatedException("there is no session behind this call"));

        ProcessInstance instance = loadInstancePort
                .findById(InstanceId.of(command.instance()))
                .orElseThrow(InstanceNotFoundException::new);

        StepId stepId = StepId.of(UUID.randomUUID());

        List<StepDependency> waitsFor = edgesInto(instance, stepId, command);

        InstanceStep step = command.isAttach()
                ? attachedStep(stepId, TaskRef.of(command.task()), instance, now)
                : createdStep(stepId, command, instance, now);

        ProcessInstance widened = instance.with(step, waitsFor, now);

        saveInstancePort.addStep(widened.id(), widened.stepOf(stepId));
        saveInstancePort.replaceEdges(widened.id(), widened.dependencies());
        saveInstancePort.updateAll(widened);
        taskProvenancePort.link(step.task(), widened.id(), stepId);
        appendProcessEventPort.append(
                ProcessEvent.onInstance(widened.id(), ProcessAction.TASK_ADDED_TO_INSTANCE, caller, now));
        return new AddTaskToInstanceUseCase.Added(
                widened, stepId.value(), step.task().value());
    }

    private InstanceStep attachedStep(StepId id, TaskRef task, ProcessInstance instance, Instant now) {
        AttachableTasksPort.Attachable found =
                attachableTasksPort.describe(task).orElseThrow(TaskNotAttachableException::new);
        if (found.inAProcess()) {
            throw new TaskAlreadyInAProcessException();
        }
        return InstanceStep.attached(
                id,
                task,
                found.assigneeId() == null ? null : PersonId.of(found.assigneeId()),
                found.title(),
                instance.steps().size(),
                found.closed(),
                now);
    }

    private InstanceStep createdStep(
            StepId id, AddTaskToInstanceCommand command, ProcessInstance instance, Instant now) {
        AddTaskToInstanceCommand.NewTask asked = command.newTask();
        TaskRef created = createTaskPort.createFor(
                instance.id(),
                id,
                asked.title(),
                asked.description(),
                PersonId.of(asked.assignee()),
                instance.startedBy(),
                asked.deadline(),
                asked.priority(),
                null);
        return InstanceStep.attached(
                id,
                created,
                PersonId.of(asked.assignee()),
                asked.title(),
                instance.steps().size(),
                false,
                now);
    }

    private static List<StepDependency> edgesInto(
            ProcessInstance instance, StepId newStep, AddTaskToInstanceCommand command) {
        List<StepDependency> edges = new ArrayList<>();
        for (UUID waitsForId : new LinkedHashSet<>(command.waitsFor())) {
            StepId waitsFor = StepId.of(waitsForId);
            instance.stepOf(waitsFor);
            edges.add(new StepDependency(newStep, waitsFor));
        }
        return edges;
    }
}
