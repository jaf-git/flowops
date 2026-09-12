package com.flowops.process.application.viewinstance;

import com.flowops.process.application.shared.exception.InstanceNotFoundException;
import com.flowops.process.application.shared.exception.NotAuthenticatedException;
import com.flowops.process.application.shared.port.CallerPermissionsPort;
import com.flowops.process.application.shared.port.IdentifyCallerPort;
import com.flowops.process.application.shared.port.LoadInstancePort;
import com.flowops.process.application.shared.port.LoadTemplatePort;
import com.flowops.process.application.shared.port.ReportingLinePort;
import com.flowops.process.application.shared.port.TaskStatePort;
import com.flowops.process.domain.model.InstanceId;
import com.flowops.process.domain.model.InstanceStep;
import com.flowops.process.domain.model.PersonId;
import com.flowops.process.domain.model.ProcessInstance;
import com.flowops.process.domain.model.TaskRef;
import com.flowops.process.domain.model.TemplateId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ViewInstanceService implements ViewInstanceUseCase {
    private static final String SEES_EVERY_INSTANCE = "PROCESS_VIEW_ANY";
    private static final String SEES_THE_SUBTREE = "PROCESS_VIEW_SUBTREE";

    private final IdentifyCallerPort identifyCallerPort;
    private final CallerPermissionsPort callerPermissionsPort;
    private final ReportingLinePort reportingLinePort;
    private final LoadInstancePort loadInstancePort;
    private final TaskStatePort taskStatePort;
    private final LoadTemplatePort loadTemplatePort;

    public ViewInstanceService(
            IdentifyCallerPort identifyCallerPort,
            CallerPermissionsPort callerPermissionsPort,
            ReportingLinePort reportingLinePort,
            LoadInstancePort loadInstancePort,
            TaskStatePort taskStatePort,
            LoadTemplatePort loadTemplatePort) {
        this.identifyCallerPort = identifyCallerPort;
        this.callerPermissionsPort = callerPermissionsPort;
        this.reportingLinePort = reportingLinePort;
        this.loadInstancePort = loadInstancePort;
        this.taskStatePort = taskStatePort;
        this.loadTemplatePort = loadTemplatePort;
    }

    @Override
    @Transactional(readOnly = true)
    public List<InstanceView> visible(InstancePopulation population) {
        List<ProcessInstance> runs = inScope(population);

        Map<TemplateId, String> names = templateNames(runs);

        List<InstanceView> views = new ArrayList<>();
        for (ProcessInstance instance : runs) {
            views.add(new InstanceView(instance, java.util.Map.of(), names.get(instance.template())));
        }
        return views;
    }

    private Map<TemplateId, String> templateNames(List<ProcessInstance> runs) {
        Map<TemplateId, String> names = new java.util.HashMap<>();
        for (ProcessInstance run : runs) {
            TemplateId template = run.template();
            if (template != null && !names.containsKey(template)) {
                loadTemplatePort.findById(template).ifPresent(found -> names.put(template, found.name()));
            }
        }
        return names;
    }

    private List<ProcessInstance> inScope(InstancePopulation population) {
        Supplier<List<ProcessInstance>> everything;
        Function<Set<PersonId>, List<ProcessInstance>> theirs;
        if (population == InstancePopulation.EVERY_RUN) {
            everything = loadInstancePort::findAll;
            theirs = loadInstancePort::findInvolving;
        } else {
            everything = loadInstancePort::findOnTheBoard;
            theirs = loadInstancePort::findOnTheBoardInvolving;
        }
        return callerPermissionsPort.callerHolds(SEES_EVERY_INSTANCE)
                ? everything.get()
                : theirs.apply(peopleInScope());
    }

    @Override
    @Transactional(readOnly = true)
    public InstanceView one(InstanceId id) {
        ProcessInstance instance = loadInstancePort.findById(id).orElseThrow(InstanceNotFoundException::new);
        if (!callerPermissionsPort.callerHolds(SEES_EVERY_INSTANCE)) {
            Set<PersonId> scope = peopleInScope();
            if (!scope.contains(instance.owner()) && !instance.heldByAnyOf(scope)) {
                throw new InstanceNotFoundException();
            }
        }
        return new InstanceView(
                instance,
                progressOf(instance),
                instance.template() == null
                        ? null
                        : loadTemplatePort
                                .findById(instance.template())
                                .map(com.flowops.process.domain.model.ProcessTemplate::name)
                                .orElse(null));
    }

    private java.util.Map<TaskRef, TaskStatePort.TaskProgress> progressOf(ProcessInstance instance) {
        List<TaskRef> held = new ArrayList<>();
        for (InstanceStep step : instance.steps()) {
            if (step.task() != null) {
                held.add(step.task());
            }
        }
        return held.isEmpty() ? java.util.Map.of() : taskStatePort.describe(held);
    }

    private Set<PersonId> peopleInScope() {
        PersonId caller = identifyCallerPort
                .currentCaller()
                .orElseThrow(() -> new NotAuthenticatedException("there is no session behind this call"));
        Set<PersonId> scope = new LinkedHashSet<>();
        scope.add(caller);
        if (callerPermissionsPort.callerHolds(SEES_THE_SUBTREE)) {
            scope.addAll(reportingLinePort.subtreeOf(caller));
        }
        return scope;
    }
}
