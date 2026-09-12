package com.flowops.process.infrastructure.task;

import com.flowops.process.application.shared.port.TaskStatePort;
import com.flowops.process.domain.model.TaskRef;
import com.flowops.task.application.viewtaskstate.DescribeTaskStateUseCase;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TaskStateAdapter implements TaskStatePort {
    private static final Set<String> QUEUE_TIME = Set.of("WAIT", "BLOCKED", "REVIEW");

    private final DescribeTaskStateUseCase describeTaskStateUseCase;

    public TaskStateAdapter(DescribeTaskStateUseCase describeTaskStateUseCase) {
        this.describeTaskStateUseCase = describeTaskStateUseCase;
    }

    @Override
    public Map<TaskRef, TaskProgress> describe(Collection<TaskRef> tasks) {
        List<UUID> ids = new ArrayList<>();
        for (TaskRef task : tasks) {
            ids.add(task.value());
        }
        Map<TaskRef, TaskProgress> progress = new LinkedHashMap<>();
        for (DescribeTaskStateUseCase.TaskSnapshot snapshot : describeTaskStateUseCase.describe(ids)) {
            List<TaskStatePort.Phase> phases = new ArrayList<>();
            for (DescribeTaskStateUseCase.Phase phase : snapshot.phases()) {
                phases.add(new TaskStatePort.Phase(phase.kind(), phase.seconds()));
            }
            progress.put(
                    TaskRef.of(snapshot.task()),
                    new TaskProgress(
                            snapshot.title(),
                            snapshot.description(),
                            snapshot.priority(),
                            snapshot.state(),
                            snapshot.blockedReason(),
                            snapshot.assignee(),
                            snapshot.deadline(),
                            snapshot.atRisk(),
                            phases,
                            queueTimeOf(snapshot)));
        }
        return progress;
    }

    private static Duration queueTimeOf(DescribeTaskStateUseCase.TaskSnapshot snapshot) {
        long seconds = 0;
        for (DescribeTaskStateUseCase.Phase phase : snapshot.phases()) {
            if (QUEUE_TIME.contains(phase.kind())) {
                seconds += phase.seconds();
            }
        }
        return Duration.ofSeconds(seconds);
    }
}
