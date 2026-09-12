package com.flowops.process.application.sweep;

import com.flowops.process.application.advancegraph.AdvanceGraphUseCase;
import com.flowops.process.application.shared.port.LoadInstancePort;
import com.flowops.process.application.shared.port.TaskStatePort;
import com.flowops.process.domain.model.InstanceStep;
import com.flowops.process.domain.model.ProcessInstance;
import com.flowops.process.domain.model.TaskRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReachabilitySweep {
    private static final Logger LOG = LoggerFactory.getLogger(ReachabilitySweep.class);

    private final LoadInstancePort loadInstancePort;
    private final TaskStatePort taskStatePort;
    private final AdvanceGraphUseCase advanceGraphUseCase;

    public ReachabilitySweep(
            LoadInstancePort loadInstancePort, TaskStatePort taskStatePort, AdvanceGraphUseCase advanceGraphUseCase) {
        this.loadInstancePort = loadInstancePort;
        this.advanceGraphUseCase = advanceGraphUseCase;
        this.taskStatePort = taskStatePort;
    }

    @Scheduled(fixedDelayString = "${flowops.process.sweep-interval-ms:300000}")
    public void reconcile() {
        List<ProcessInstance> running = loadInstancePort.findRunning();
        int advanced = 0;
        for (ProcessInstance instance : running) {
            advanced += reconcileOne(instance);
        }
        if (advanced > 0) {
            LOG.info("reachability sweep advanced {} step(s) across {} running instance(s)", advanced, running.size());
        }
    }

    private int reconcileOne(ProcessInstance instance) {
        List<TaskRef> held = new ArrayList<>();
        for (InstanceStep step : instance.steps()) {
            if (step.task() != null && !step.isClosed()) {
                held.add(step.task());
            }
        }
        if (held.isEmpty()) {
            return 0;
        }

        Map<TaskRef, TaskStatePort.TaskProgress> progress;
        try {
            progress = taskStatePort.describe(held);
        } catch (RuntimeException failure) {
            LOG.warn(
                    "reachability sweep could not read the tasks of instance {}",
                    instance.id().value(),
                    failure);
            return 0;
        }
        int advanced = 0;
        for (Map.Entry<TaskRef, TaskStatePort.TaskProgress> each : progress.entrySet()) {
            try {
                advanceGraphUseCase.onTaskState(
                        each.getKey().value(), each.getValue().state(), true);
                advanced++;
            } catch (RuntimeException failure) {
                LOG.warn(
                        "reachability sweep could not advance task {}",
                        each.getKey().value(),
                        failure);
            }
        }
        return advanced;
    }
}
