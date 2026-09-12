package com.flowops.process.application.startfromtasks;

import com.flowops.process.application.shared.exception.NotAuthenticatedException;
import com.flowops.process.application.shared.port.AppendProcessEventPort;
import com.flowops.process.application.shared.port.CreateTaskPort;
import com.flowops.process.application.shared.port.IdentifyCallerPort;
import com.flowops.process.application.shared.port.LoadPersonPort;
import com.flowops.process.application.shared.port.SaveInstancePort;
import com.flowops.process.application.shared.port.TaskProvenancePort;
import com.flowops.process.domain.enums.ProcessAction;
import com.flowops.process.domain.event.ProcessEvent;
import com.flowops.process.domain.exception.ProcessOwnerNotActiveException;
import com.flowops.process.domain.exception.TemplateNeedsAStepException;
import com.flowops.process.domain.model.InstanceId;
import com.flowops.process.domain.model.InstanceStep;
import com.flowops.process.domain.model.PersonId;
import com.flowops.process.domain.model.ProcessInstance;
import com.flowops.process.domain.model.StepId;
import com.flowops.process.domain.model.TaskRef;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StartFromDescriptionsService implements StartFromDescriptionsUseCase {
    private final IdentifyCallerPort identifyCallerPort;
    private final LoadPersonPort loadPersonPort;
    private final CreateTaskPort createTaskPort;
    private final SaveInstancePort saveInstancePort;
    private final TaskProvenancePort taskProvenancePort;
    private final AppendProcessEventPort appendProcessEventPort;
    private final Clock clock;

    public StartFromDescriptionsService(
            IdentifyCallerPort identifyCallerPort,
            LoadPersonPort loadPersonPort,
            CreateTaskPort createTaskPort,
            SaveInstancePort saveInstancePort,
            TaskProvenancePort taskProvenancePort,
            AppendProcessEventPort appendProcessEventPort,
            Clock clock) {
        this.identifyCallerPort = identifyCallerPort;
        this.loadPersonPort = loadPersonPort;
        this.createTaskPort = createTaskPort;
        this.saveInstancePort = saveInstancePort;
        this.taskProvenancePort = taskProvenancePort;
        this.appendProcessEventPort = appendProcessEventPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ProcessInstance execute(StartFromDescriptionsCommand command) {
        Instant now = clock.instant();
        PersonId startedBy = identifyCallerPort
                .currentCaller()
                .orElseThrow(() -> new NotAuthenticatedException("there is no session behind this call"));

        if (command.steps().isEmpty()) {
            throw new TemplateNeedsAStepException();
        }

        loadPersonPort
                .describe(command.processOwner())
                .filter(LoadPersonPort.Person::active)
                .orElseThrow(ProcessOwnerNotActiveException::new);

        InstanceId id = InstanceId.of(UUID.randomUUID());
        List<InstanceStep> steps = new ArrayList<>();

        for (StartFromDescriptionsCommand.NewStep described : command.steps()) {
            StepId stepId = StepId.of(UUID.randomUUID());
            TaskRef task = createTaskPort.createFor(
                    id,
                    stepId,
                    described.title(),
                    described.description(),
                    described.assignee(),
                    startedBy,
                    described.deadline(),
                    described.priority(),
                    null);

            steps.add(InstanceStep.attached(
                    stepId, task, described.assignee(), described.title(), steps.size(), false, now));
        }

        ProcessInstance instance =
                ProcessInstance.startedFromTasks(id, command.name(), command.processOwner(), startedBy, steps, now);

        saveInstancePort.create(instance);
        for (InstanceStep step : instance.steps()) {
            taskProvenancePort.link(step.task(), id, step.id());
        }
        appendProcessEventPort.append(ProcessEvent.onInstance(id, ProcessAction.INSTANCE_STARTED, startedBy, now));

        return instance;
    }
}
