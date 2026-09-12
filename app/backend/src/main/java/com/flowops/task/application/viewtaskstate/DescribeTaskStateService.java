package com.flowops.task.application.viewtaskstate;

import com.flowops.task.application.shared.AtRiskRule;
import com.flowops.task.application.shared.port.LoadTaskPort;
import com.flowops.task.application.shared.port.LoadTransitionPort;
import com.flowops.task.application.shared.port.PhaseTimerPort;
import com.flowops.task.domain.enums.TaskState;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.PhaseTimer;
import com.flowops.task.domain.model.Task;
import com.flowops.task.domain.model.TaskId;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DescribeTaskStateService implements DescribeTaskStateUseCase {
    private final LoadTaskPort loadTaskPort;
    private final LoadTransitionPort loadTransitionPort;
    private final PhaseTimerPort phaseTimerPort;
    private final AtRiskRule atRiskRule;
    private final Clock clock;

    public DescribeTaskStateService(
            LoadTaskPort loadTaskPort,
            LoadTransitionPort loadTransitionPort,
            PhaseTimerPort phaseTimerPort,
            AtRiskRule atRiskRule,
            Clock clock) {
        this.loadTaskPort = loadTaskPort;
        this.loadTransitionPort = loadTransitionPort;
        this.phaseTimerPort = phaseTimerPort;
        this.atRiskRule = atRiskRule;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskSnapshot> describe(Collection<UUID> tasks) {
        List<TaskSnapshot> described = new ArrayList<>();
        for (UUID id : tasks) {
            Optional<Task> found = loadTaskPort.findById(TaskId.of(id));
            if (found.isEmpty()) {
                continue;
            }
            Task task = found.get();
            String reason = task.state() == TaskState.BLOCKED
                    ? loadTransitionPort.currentBlockReasonOf(task.id()).orElse(null)
                    : null;
            described.add(new TaskSnapshot(
                    id,
                    task.title(),
                    task.description().orElse(null),
                    task.priority().name(),
                    task.state().name(),
                    reason,
                    task.assignee().map(PersonId::value).orElse(null),
                    task.deadline(),
                    atRiskRule.of(task),
                    secondsPerKind(task.id())));
        }
        return described;
    }

    private List<Phase> secondsPerKind(TaskId task) {
        Map<String, Long> perKind = new LinkedHashMap<>();
        for (PhaseTimer phase : phaseTimerPort.allPhasesOf(task)) {
            long seconds = Duration.between(
                            phase.startedAt(), phase.endedAt() == null ? clock.instant() : phase.endedAt())
                    .toSeconds();
            perKind.merge(phase.kind().name(), seconds, Long::sum);
        }
        List<Phase> phases = new ArrayList<>();
        perKind.forEach((kind, seconds) -> phases.add(new Phase(kind, seconds)));
        return phases;
    }
}
