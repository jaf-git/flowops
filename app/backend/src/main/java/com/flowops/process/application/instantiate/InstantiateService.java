package com.flowops.process.application.instantiate;

import com.flowops.process.application.shared.exception.NotAuthenticatedException;
import com.flowops.process.application.shared.exception.StepTemplateUnavailableException;
import com.flowops.process.application.shared.exception.TemplateNotFoundException;
import com.flowops.process.application.shared.port.AppendProcessEventPort;
import com.flowops.process.application.shared.port.IdentifyCallerPort;
import com.flowops.process.application.shared.port.LoadPersonPort;
import com.flowops.process.application.shared.port.LoadTemplatePort;
import com.flowops.process.application.shared.port.NotifyProcessPort;
import com.flowops.process.application.shared.port.SaveInstancePort;
import com.flowops.process.application.shared.port.TaskTemplateContentPort;
import com.flowops.process.domain.enums.ProcessAction;
import com.flowops.process.domain.event.ProcessEvent;
import com.flowops.process.domain.exception.ProcessOwnerNotActiveException;
import com.flowops.process.domain.exception.TemplateIsRetiredException;
import com.flowops.process.domain.model.InstanceId;
import com.flowops.process.domain.model.PersonId;
import com.flowops.process.domain.model.ProcessInstance;
import com.flowops.process.domain.model.ProcessTemplate;
import com.flowops.process.domain.model.StepDefinition;
import com.flowops.process.domain.model.StepId;
import com.flowops.process.domain.model.TaskTemplateRef;
import com.flowops.process.domain.model.TaskTemplateWork;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InstantiateService implements InstantiateUseCase {
    private final IdentifyCallerPort identifyCallerPort;
    private final LoadTemplatePort loadTemplatePort;
    private final LoadPersonPort loadPersonPort;
    private final SaveInstancePort saveInstancePort;
    private final AppendProcessEventPort appendProcessEventPort;
    private final NotifyProcessPort notifyProcessPort;
    private final TaskTemplateContentPort taskTemplateContentPort;
    private final Clock clock;

    public InstantiateService(
            IdentifyCallerPort identifyCallerPort,
            LoadTemplatePort loadTemplatePort,
            LoadPersonPort loadPersonPort,
            SaveInstancePort saveInstancePort,
            AppendProcessEventPort appendProcessEventPort,
            NotifyProcessPort notifyProcessPort,
            TaskTemplateContentPort taskTemplateContentPort,
            Clock clock) {
        this.identifyCallerPort = identifyCallerPort;
        this.loadTemplatePort = loadTemplatePort;
        this.loadPersonPort = loadPersonPort;
        this.saveInstancePort = saveInstancePort;
        this.appendProcessEventPort = appendProcessEventPort;
        this.notifyProcessPort = notifyProcessPort;
        this.taskTemplateContentPort = taskTemplateContentPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ProcessInstance execute(InstantiateCommand command) {
        Instant now = clock.instant();
        PersonId startedBy = identifyCallerPort
                .currentCaller()
                .orElseThrow(() -> new NotAuthenticatedException("there is no session behind this call"));

        ProcessTemplate template =
                loadTemplatePort.findById(command.template()).orElseThrow(TemplateNotFoundException::new);

        if (!template.active()) {
            throw new TemplateIsRetiredException();
        }

        loadPersonPort
                .describe(command.processOwner())
                .filter(LoadPersonPort.Person::active)
                .orElseThrow(ProcessOwnerNotActiveException::new);

        List<TaskTemplateRef> referenced = new ArrayList<>();
        for (StepDefinition step : template.steps()) {
            referenced.add(step.taskTemplateId());
        }
        Map<TaskTemplateRef, TaskTemplateWork> work = taskTemplateContentPort.contentOf(referenced);
        for (StepDefinition step : template.steps()) {
            if (!work.containsKey(step.taskTemplateId())) {
                throw new StepTemplateUnavailableException(step.position());
            }
        }

        ProcessInstance instance = ProcessInstance.cutFrom(
                InstanceId.of(UUID.randomUUID()),
                template,
                work,
                command.name(),
                command.processOwner(),
                startedBy,
                now);

        saveInstancePort.create(instance);
        appendProcessEventPort.append(
                ProcessEvent.onInstance(instance.id(), ProcessAction.INSTANCE_STARTED, startedBy, now));

        for (StepId reachable : instance.awaitingAssignment()) {
            notifyProcessPort.stepReachable(instance.id(), reachable, command.processOwner());
        }
        return instance;
    }
}
