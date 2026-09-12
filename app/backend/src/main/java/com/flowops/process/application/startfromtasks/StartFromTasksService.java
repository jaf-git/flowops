package com.flowops.process.application.startfromtasks;

import com.flowops.process.application.shared.exception.NotAuthenticatedException;
import com.flowops.process.application.shared.exception.TaskAlreadyInAProcessException;
import com.flowops.process.application.shared.exception.TaskNotAttachableException;
import com.flowops.process.application.shared.port.AppendProcessEventPort;
import com.flowops.process.application.shared.port.AttachableTasksPort;
import com.flowops.process.application.shared.port.IdentifyCallerPort;
import com.flowops.process.application.shared.port.LoadPersonPort;
import com.flowops.process.application.shared.port.NotifyProcessPort;
import com.flowops.process.application.shared.port.SaveInstancePort;
import com.flowops.process.application.shared.port.TaskProvenancePort;
import com.flowops.process.domain.enums.ProcessAction;
import com.flowops.process.domain.event.ProcessEvent;
import com.flowops.process.domain.exception.ProcessOwnerNotActiveException;
import com.flowops.process.domain.model.InstanceId;
import com.flowops.process.domain.model.InstanceStep;
import com.flowops.process.domain.model.PersonId;
import com.flowops.process.domain.model.ProcessInstance;
import com.flowops.process.domain.model.StepId;
import com.flowops.process.domain.model.TaskRef;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StartFromTasksService implements StartFromTasksUseCase {
    private final IdentifyCallerPort identifyCallerPort;
    private final LoadPersonPort loadPersonPort;
    private final AttachableTasksPort attachableTasksPort;
    private final SaveInstancePort saveInstancePort;
    private final TaskProvenancePort taskProvenancePort;
    private final AppendProcessEventPort appendProcessEventPort;
    private final NotifyProcessPort notifyProcessPort;
    private final Clock clock;

    public StartFromTasksService(
            IdentifyCallerPort identifyCallerPort,
            LoadPersonPort loadPersonPort,
            AttachableTasksPort attachableTasksPort,
            SaveInstancePort saveInstancePort,
            TaskProvenancePort taskProvenancePort,
            AppendProcessEventPort appendProcessEventPort,
            NotifyProcessPort notifyProcessPort,
            Clock clock) {
        this.identifyCallerPort = identifyCallerPort;
        this.loadPersonPort = loadPersonPort;
        this.attachableTasksPort = attachableTasksPort;
        this.saveInstancePort = saveInstancePort;
        this.taskProvenancePort = taskProvenancePort;
        this.appendProcessEventPort = appendProcessEventPort;
        this.notifyProcessPort = notifyProcessPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ProcessInstance execute(StartFromTasksCommand command) {
        Instant now = clock.instant();
        PersonId startedBy = identifyCallerPort
                .currentCaller()
                .orElseThrow(() -> new NotAuthenticatedException("there is no session behind this call"));

        loadPersonPort
                .describe(command.processOwner())
                .filter(LoadPersonPort.Person::active)
                .orElseThrow(ProcessOwnerNotActiveException::new);

        InstanceId id = InstanceId.of(UUID.randomUUID());
        List<InstanceStep> steps = stepsOver(command.tasks(), now);

        ProcessInstance instance =
                ProcessInstance.startedFromTasks(id, command.name(), command.processOwner(), startedBy, steps, now);

        saveInstancePort.create(instance);
        for (InstanceStep step : instance.steps()) {
            taskProvenancePort.link(step.task(), id, step.id());
        }
        appendProcessEventPort.append(ProcessEvent.onInstance(id, ProcessAction.INSTANCE_STARTED, startedBy, now));

        if (instance.isComplete()) {
            appendProcessEventPort.append(
                    ProcessEvent.onInstance(id, ProcessAction.INSTANCE_COMPLETED, command.processOwner(), now));
            notifyProcessPort.instanceComplete(id, command.processOwner());
        }
        return instance;
    }

    private List<InstanceStep> stepsOver(List<TaskRef> tasks, Instant now) {
        Set<TaskRef> seen = new LinkedHashSet<>();
        List<InstanceStep> steps = new ArrayList<>();
        for (TaskRef task : tasks) {
            if (!seen.add(task)) {
                throw new TaskAlreadyInAProcessException();
            }
            AttachableTasksPort.Attachable found =
                    attachableTasksPort.describe(task).orElseThrow(TaskNotAttachableException::new);
            if (found.inAProcess()) {
                throw new TaskAlreadyInAProcessException();
            }
            steps.add(InstanceStep.attached(
                    StepId.of(UUID.randomUUID()),
                    task,
                    found.assigneeId() == null ? null : PersonId.of(found.assigneeId()),
                    found.title(),
                    steps.size(),
                    found.closed(),
                    now));
        }
        return steps;
    }
}
