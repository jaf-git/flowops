package com.flowops.process.application.editinstance;

import com.flowops.process.application.shared.exception.InstanceNotFoundException;
import com.flowops.process.application.shared.exception.NotAuthenticatedException;
import com.flowops.process.application.shared.port.AppendProcessEventPort;
import com.flowops.process.application.shared.port.IdentifyCallerPort;
import com.flowops.process.application.shared.port.LoadInstancePort;
import com.flowops.process.application.shared.port.NotifyProcessPort;
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
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EditInstanceGraphService implements EditInstanceGraphUseCase {
    private final IdentifyCallerPort identifyCallerPort;
    private final LoadInstancePort loadInstancePort;
    private final SaveInstancePort saveInstancePort;
    private final TaskProvenancePort taskProvenancePort;
    private final AppendProcessEventPort appendProcessEventPort;
    private final NotifyProcessPort notifyProcessPort;
    private final Clock clock;

    public EditInstanceGraphService(
            IdentifyCallerPort identifyCallerPort,
            LoadInstancePort loadInstancePort,
            SaveInstancePort saveInstancePort,
            TaskProvenancePort taskProvenancePort,
            AppendProcessEventPort appendProcessEventPort,
            NotifyProcessPort notifyProcessPort,
            Clock clock) {
        this.identifyCallerPort = identifyCallerPort;
        this.loadInstancePort = loadInstancePort;
        this.saveInstancePort = saveInstancePort;
        this.taskProvenancePort = taskProvenancePort;
        this.appendProcessEventPort = appendProcessEventPort;
        this.notifyProcessPort = notifyProcessPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ProcessInstance remove(InstanceId id, StepId step) {
        Instant now = clock.instant();
        PersonId caller = caller();
        ProcessInstance instance = load(id);

        InstanceStep leaving = instance.stepOf(step);
        ProcessInstance narrowed = instance.without(step, now);

        saveInstancePort.removeStep(id, step);
        saveInstancePort.replaceEdges(id, narrowed.dependencies());
        saveInstancePort.updateAll(narrowed);

        saveInstancePort.updatePositions(narrowed);
        if (leaving.task() != null) {
            taskProvenancePort.unlink(leaving.task());
        }
        appendProcessEventPort.append(
                ProcessEvent.onInstance(id, ProcessAction.TASK_REMOVED_FROM_INSTANCE, caller, now));
        tellTheSteerer(instance, narrowed);
        return narrowed;
    }

    @Override
    @Transactional
    public ProcessInstance reorder(InstanceId id, List<StepId> order) {
        Instant now = clock.instant();
        PersonId caller = caller();
        ProcessInstance reordered = load(id).reordered(order);

        saveInstancePort.updatePositions(reordered);
        appendProcessEventPort.append(ProcessEvent.onInstance(id, ProcessAction.INSTANCE_TASKS_REORDERED, caller, now));
        return reordered;
    }

    @Override
    @Transactional
    public ProcessInstance draw(InstanceId id, StepDependency edge) {
        return rewire(id, edge, true);
    }

    @Override
    @Transactional
    public ProcessInstance erase(InstanceId id, StepDependency edge) {
        return rewire(id, edge, false);
    }

    private ProcessInstance rewire(InstanceId id, StepDependency edge, boolean drawing) {
        Instant now = clock.instant();
        PersonId caller = caller();
        ProcessInstance instance = load(id);

        ProcessInstance changed = drawing ? instance.withEdge(edge, now) : instance.withoutEdge(edge, now);
        if (changed == instance) {
            return instance;
        }

        saveInstancePort.replaceEdges(id, changed.dependencies());
        saveInstancePort.updateAll(changed);
        appendProcessEventPort.append(ProcessEvent.onInstance(
                id, drawing ? ProcessAction.DEPENDENCY_DEFINED : ProcessAction.DEPENDENCY_REMOVED, caller, now));
        tellTheSteerer(instance, changed);
        return changed;
    }

    private void tellTheSteerer(ProcessInstance before, ProcessInstance after) {
        for (StepId opened : after.newlyReachableSince(before)) {
            notifyProcessPort.stepReachable(after.id(), opened, after.owner());
        }
    }

    private ProcessInstance load(InstanceId id) {
        return loadInstancePort.findById(id).orElseThrow(InstanceNotFoundException::new);
    }

    private PersonId caller() {
        return identifyCallerPort
                .currentCaller()
                .orElseThrow(() -> new NotAuthenticatedException("there is no session behind this call"));
    }
}
