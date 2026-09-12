package com.flowops.task.application.overridetask;

import com.flowops.task.application.shared.AtRiskRule;
import com.flowops.task.application.shared.TaskTransitionResult;
import com.flowops.task.application.shared.TaskWriter;
import com.flowops.task.application.shared.exception.NotAuthenticatedException;
import com.flowops.task.application.shared.exception.TaskNotFoundException;
import com.flowops.task.application.shared.port.IdentifyCallerPort;
import com.flowops.task.application.shared.port.LoadPersonPort;
import com.flowops.task.application.shared.port.LoadTaskPort;
import com.flowops.task.application.shared.port.NotifyTaskProgressPort;
import com.flowops.task.application.shared.port.PhaseTimerPort;
import com.flowops.task.domain.enums.TaskState;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.PhaseTimer;
import com.flowops.task.domain.model.Task;
import com.flowops.task.domain.model.TaskId;
import com.flowops.task.domain.model.TaskMove;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OverrideTaskService implements OverrideTaskUseCase {
    private final IdentifyCallerPort identifyCallerPort;
    private final LoadTaskPort loadTaskPort;
    private final PhaseTimerPort phaseTimerPort;
    private final LoadPersonPort loadPersonPort;
    private final TaskWriter taskWriter;
    private final NotifyTaskProgressPort notifyTaskProgressPort;
    private final AtRiskRule atRiskRule;
    private final Clock clock;

    public OverrideTaskService(
            IdentifyCallerPort identifyCallerPort,
            LoadTaskPort loadTaskPort,
            PhaseTimerPort phaseTimerPort,
            LoadPersonPort loadPersonPort,
            TaskWriter taskWriter,
            NotifyTaskProgressPort notifyTaskProgressPort,
            AtRiskRule atRiskRule,
            Clock clock) {
        this.identifyCallerPort = identifyCallerPort;
        this.loadTaskPort = loadTaskPort;
        this.phaseTimerPort = phaseTimerPort;
        this.loadPersonPort = loadPersonPort;
        this.taskWriter = taskWriter;
        this.notifyTaskProgressPort = notifyTaskProgressPort;
        this.atRiskRule = atRiskRule;
        this.clock = clock;
    }

    @Override
    @Transactional
    public TaskTransitionResult execute(TaskId task, TaskState target, String reason) {
        Instant now = clock.instant();
        PersonId owner = identifyCallerPort
                .currentCaller()
                .orElseThrow(() -> new NotAuthenticatedException("there is no session behind this call"));

        Task found = loadTaskPort
                .lockForTransition(task)
                .orElseThrow(() -> new TaskNotFoundException("there is no such task"));

        PhaseTimer open = phaseTimerPort.openPhaseOf(task).orElse(null);

        TaskMove move = TaskMove.overridden(found, open, target, owner, reason, now);
        taskWriter.writeMove(move);

        notifyTaskProgressPort.overridden(task, found.assignee().orElse(null), found.creator(), target.name(), reason);

        return new TaskTransitionResult(
                move.task(),
                move.task()
                        .assignee()
                        .flatMap(loadPersonPort::describe)
                        .map(LoadPersonPort.Person::displayName)
                        .orElse(""),
                atRiskRule.of(move.task()));
    }
}
