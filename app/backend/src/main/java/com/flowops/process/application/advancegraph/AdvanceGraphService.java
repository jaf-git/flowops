package com.flowops.process.application.advancegraph;

import com.flowops.process.application.shared.port.AppendProcessEventPort;
import com.flowops.process.application.shared.port.LoadInstancePort;
import com.flowops.process.application.shared.port.NotifyProcessPort;
import com.flowops.process.application.shared.port.SaveInstancePort;
import com.flowops.process.domain.enums.ProcessAction;
import com.flowops.process.domain.event.ProcessEvent;
import com.flowops.process.domain.model.InstanceStep;
import com.flowops.process.domain.model.ProcessInstance;
import com.flowops.process.domain.model.StepId;
import com.flowops.process.domain.model.TaskRef;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdvanceGraphService implements AdvanceGraphUseCase {
    private static final String CLOSED = "CLOSED";
    private static final String CREATED = "CREATED";

    private final LoadInstancePort loadInstancePort;
    private final SaveInstancePort saveInstancePort;
    private final AppendProcessEventPort appendProcessEventPort;
    private final NotifyProcessPort notifyProcessPort;
    private final Clock clock;

    public AdvanceGraphService(
            LoadInstancePort loadInstancePort,
            SaveInstancePort saveInstancePort,
            AppendProcessEventPort appendProcessEventPort,
            NotifyProcessPort notifyProcessPort,
            Clock clock) {
        this.loadInstancePort = loadInstancePort;
        this.saveInstancePort = saveInstancePort;
        this.appendProcessEventPort = appendProcessEventPort;
        this.notifyProcessPort = notifyProcessPort;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTaskState(UUID task, String state, boolean assigned) {
        Optional<ProcessInstance> owning = loadInstancePort.findByTask(TaskRef.of(task));
        if (owning.isEmpty()) {
            return;
        }
        ProcessInstance before = owning.get();
        StepId step = stepHolding(before, task);
        Instant now = clock.instant();

        if (CLOSED.equals(state)) {
            advance(before, step, now);
            return;
        }
        if (CREATED.equals(state) && !assigned) {
            handBack(before, step, now);
        }
    }

    private void advance(ProcessInstance before, StepId step, Instant now) {
        ProcessInstance after = before.closed(step, now);
        if (after == before) {
            return;
        }
        saveInstancePort.updateAll(after);
        appendProcessEventPort.append(
                ProcessEvent.onInstance(after.id(), ProcessAction.STEP_CLOSED, after.owner(), now));

        for (StepId opened : after.newlyReachableSince(before)) {
            appendProcessEventPort.append(
                    ProcessEvent.onInstance(after.id(), ProcessAction.STEP_REACHED, after.owner(), now));
            notifyProcessPort.stepReachable(after.id(), opened, after.owner());
        }
        if (after.isComplete() && !before.isComplete()) {
            appendProcessEventPort.append(
                    ProcessEvent.onInstance(after.id(), ProcessAction.INSTANCE_COMPLETED, after.owner(), now));
            notifyProcessPort.instanceComplete(after.id(), after.owner());
        }
    }

    private void handBack(ProcessInstance before, StepId step, Instant now) {
        ProcessInstance after = before.returnedToReachable(step);
        if (after == before) {
            return;
        }
        saveInstancePort.updateAll(after);
        appendProcessEventPort.append(
                ProcessEvent.onInstance(after.id(), ProcessAction.STEP_RETURNED_FOR_REASSIGNMENT, after.owner(), now));
        notifyProcessPort.stepReachable(after.id(), step, after.owner());
    }

    private StepId stepHolding(ProcessInstance instance, UUID task) {
        for (InstanceStep step : instance.steps()) {
            if (step.task() != null && step.task().value().equals(task)) {
                return step.id();
            }
        }
        throw new IllegalStateException("the instance was found by this task and does not hold it");
    }
}
