package com.flowops.process.application.assignstep;

import com.flowops.process.application.shared.exception.InstanceNotFoundException;
import com.flowops.process.application.shared.exception.NotAuthenticatedException;
import com.flowops.process.application.shared.port.AppendProcessEventPort;
import com.flowops.process.application.shared.port.CreateTaskPort;
import com.flowops.process.application.shared.port.IdentifyCallerPort;
import com.flowops.process.application.shared.port.LoadInstancePort;
import com.flowops.process.application.shared.port.RecordTemplateStampPort;
import com.flowops.process.application.shared.port.SaveInstancePort;
import com.flowops.process.domain.enums.ProcessAction;
import com.flowops.process.domain.event.ProcessEvent;
import com.flowops.process.domain.model.InstanceStep;
import com.flowops.process.domain.model.PersonId;
import com.flowops.process.domain.model.ProcessInstance;
import com.flowops.process.domain.model.TaskRef;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssignStepService implements AssignStepUseCase {
    private final IdentifyCallerPort identifyCallerPort;
    private final LoadInstancePort loadInstancePort;
    private final SaveInstancePort saveInstancePort;
    private final CreateTaskPort createTaskPort;
    private final AppendProcessEventPort appendProcessEventPort;
    private final RecordTemplateStampPort recordTemplateStampPort;
    private final Clock clock;

    public AssignStepService(
            IdentifyCallerPort identifyCallerPort,
            LoadInstancePort loadInstancePort,
            SaveInstancePort saveInstancePort,
            CreateTaskPort createTaskPort,
            AppendProcessEventPort appendProcessEventPort,
            RecordTemplateStampPort recordTemplateStampPort,
            Clock clock) {
        this.identifyCallerPort = identifyCallerPort;
        this.loadInstancePort = loadInstancePort;
        this.saveInstancePort = saveInstancePort;
        this.createTaskPort = createTaskPort;
        this.appendProcessEventPort = appendProcessEventPort;
        this.recordTemplateStampPort = recordTemplateStampPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ProcessInstance execute(AssignStepCommand command) {
        Instant now = clock.instant();
        PersonId caller = identifyCallerPort
                .currentCaller()
                .orElseThrow(() -> new NotAuthenticatedException("there is no session behind this call"));

        ProcessInstance instance =
                loadInstancePort.findById(command.instance()).orElseThrow(InstanceNotFoundException::new);

        InstanceStep step = instance.stepOf(command.step());
        instance.requireAssignable(step.id());

        TaskRef created = createTaskPort.createFor(
                instance.id(),
                step.id(),
                step.title(),
                step.description(),
                command.assignee(),
                instance.startedBy(),
                command.deadline(),
                "NORMAL",
                step.taskTemplateId());

        recordTemplateStampPort.stamped(step.taskTemplateId());

        ProcessInstance moved = instance.assigned(step.id(), created, command.assignee(), now);
        saveInstancePort.updateStep(moved.id(), moved.stepOf(step.id()));
        appendProcessEventPort.append(ProcessEvent.onInstance(instance.id(), ProcessAction.STEP_ASSIGNED, caller, now));
        return moved;
    }
}
