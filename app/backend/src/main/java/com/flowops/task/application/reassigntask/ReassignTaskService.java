package com.flowops.task.application.reassigntask;

import com.flowops.task.application.shared.AtRiskRule;
import com.flowops.task.application.shared.TaskTransitionResult;
import com.flowops.task.application.shared.TaskWriter;
import com.flowops.task.application.shared.exception.AssigneeNotActiveException;
import com.flowops.task.application.shared.exception.AssigneeOutOfScopeException;
import com.flowops.task.application.shared.exception.NotAuthenticatedException;
import com.flowops.task.application.shared.exception.TaskNotFoundException;
import com.flowops.task.application.shared.port.IdentifyCallerPort;
import com.flowops.task.application.shared.port.LoadPersonPort;
import com.flowops.task.application.shared.port.LoadTaskPort;
import com.flowops.task.application.shared.port.NotifyTaskProgressPort;
import com.flowops.task.application.shared.port.PhaseTimerPort;
import com.flowops.task.application.shared.port.ReportingLinePort;
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
public class ReassignTaskService implements ReassignTaskUseCase {
    private final IdentifyCallerPort identifyCallerPort;
    private final LoadTaskPort loadTaskPort;
    private final LoadPersonPort loadPersonPort;
    private final ReportingLinePort reportingLinePort;
    private final PhaseTimerPort phaseTimerPort;
    private final TaskWriter taskWriter;
    private final NotifyTaskProgressPort notifyTaskProgressPort;
    private final AtRiskRule atRiskRule;
    private final Clock clock;

    public ReassignTaskService(
            IdentifyCallerPort identifyCallerPort,
            LoadTaskPort loadTaskPort,
            LoadPersonPort loadPersonPort,
            ReportingLinePort reportingLinePort,
            PhaseTimerPort phaseTimerPort,
            TaskWriter taskWriter,
            NotifyTaskProgressPort notifyTaskProgressPort,
            AtRiskRule atRiskRule,
            Clock clock) {
        this.identifyCallerPort = identifyCallerPort;
        this.loadTaskPort = loadTaskPort;
        this.loadPersonPort = loadPersonPort;
        this.reportingLinePort = reportingLinePort;
        this.phaseTimerPort = phaseTimerPort;
        this.taskWriter = taskWriter;
        this.notifyTaskProgressPort = notifyTaskProgressPort;
        this.atRiskRule = atRiskRule;
        this.clock = clock;
    }

    @Override
    @Transactional
    public TaskTransitionResult execute(TaskId task, PersonId newAssignee, String reason) {
        Instant now = clock.instant();
        PersonId assigner = identifyCallerPort
                .currentCaller()
                .orElseThrow(() -> new NotAuthenticatedException("there is no session behind this call"));

        Task found = loadTaskPort
                .lockForTransition(task)
                .orElseThrow(() -> new TaskNotFoundException("there is no such task"));

        LoadPersonPort.Person target = loadPersonPort
                .describe(newAssignee)
                .filter(LoadPersonPort.Person::active)
                .orElseThrow(() -> new AssigneeNotActiveException("that person cannot be given work"));

        if (!reportingLinePort.isWithinScopeOf(assigner, target.id())) {
            throw new AssigneeOutOfScopeException("that person is not yours to direct");
        }

        PersonId previous = found.assignee().orElse(null);

        PhaseTimer open = phaseTimerPort
                .openPhaseOf(task)
                .orElseThrow(() -> new IllegalStateException("task " + task.value() + " has no open phase"));

        TaskMove move = TaskMove.reassigned(found, open, target.id(), assigner, reason, now);
        taskWriter.writeStateAndAssignee(move);

        notifyTaskProgressPort.reassigned(task, previous, target.id(), reason);
        return new TaskTransitionResult(move.task(), target.displayName(), atRiskRule.of(move.task()));
    }
}
