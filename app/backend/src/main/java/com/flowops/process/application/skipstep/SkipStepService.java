package com.flowops.process.application.skipstep;

import com.flowops.process.application.shared.exception.InstanceNotFoundException;
import com.flowops.process.application.shared.port.AppendProcessEventPort;
import com.flowops.process.application.shared.port.LoadInstancePort;
import com.flowops.process.application.shared.port.NotifyProcessPort;
import com.flowops.process.application.shared.port.SaveInstancePort;
import com.flowops.process.domain.enums.ProcessAction;
import com.flowops.process.domain.event.ProcessEvent;
import com.flowops.process.domain.model.InstanceId;
import com.flowops.process.domain.model.ProcessInstance;
import com.flowops.process.domain.model.StepId;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkipStepService implements SkipStepUseCase {
    private final LoadInstancePort loadInstancePort;
    private final SaveInstancePort saveInstancePort;
    private final AppendProcessEventPort appendProcessEventPort;
    private final NotifyProcessPort notifyProcessPort;
    private final Clock clock;

    public SkipStepService(
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
    @Transactional
    public void skip(InstanceId instance, StepId step) {
        ProcessInstance before = loadInstancePort.findById(instance).orElseThrow(InstanceNotFoundException::new);
        Instant now = clock.instant();

        ProcessInstance after = before.skipped(step, now);
        if (after == before) {
            return;
        }

        saveInstancePort.updateAll(after);
        appendProcessEventPort.append(
                ProcessEvent.onInstance(after.id(), ProcessAction.STEP_SKIPPED, after.owner(), now));
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
}
