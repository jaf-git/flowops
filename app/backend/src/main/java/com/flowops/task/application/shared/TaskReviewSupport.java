package com.flowops.task.application.shared;

import com.flowops.task.application.shared.exception.NotAuthenticatedException;
import com.flowops.task.application.shared.exception.TaskNotFoundException;
import com.flowops.task.application.shared.exception.TaskOutOfScopeException;
import com.flowops.task.application.shared.port.CallerPermissionsPort;
import com.flowops.task.application.shared.port.IdentifyCallerPort;
import com.flowops.task.application.shared.port.LoadPersonPort;
import com.flowops.task.application.shared.port.LoadTaskPort;
import com.flowops.task.application.shared.port.PhaseTimerPort;
import com.flowops.task.application.shared.port.ReportingLinePort;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.PhaseTimer;
import com.flowops.task.domain.model.Task;
import com.flowops.task.domain.model.TaskId;
import com.flowops.task.domain.model.TaskMove;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class TaskReviewSupport {
    private static final String VIEW_ANY = "TASK_VIEW_ANY";

    private final IdentifyCallerPort identifyCallerPort;
    private final CallerPermissionsPort callerPermissionsPort;
    private final ReportingLinePort reportingLinePort;
    private final LoadTaskPort loadTaskPort;
    private final PhaseTimerPort phaseTimerPort;
    private final LoadPersonPort loadPersonPort;
    private final TaskWriter taskWriter;
    private final AtRiskRule atRiskRule;
    private final Clock clock;

    public TaskReviewSupport(
            IdentifyCallerPort identifyCallerPort,
            CallerPermissionsPort callerPermissionsPort,
            ReportingLinePort reportingLinePort,
            LoadTaskPort loadTaskPort,
            PhaseTimerPort phaseTimerPort,
            LoadPersonPort loadPersonPort,
            TaskWriter taskWriter,
            AtRiskRule atRiskRule,
            Clock clock) {
        this.identifyCallerPort = identifyCallerPort;
        this.callerPermissionsPort = callerPermissionsPort;
        this.reportingLinePort = reportingLinePort;
        this.loadTaskPort = loadTaskPort;
        this.phaseTimerPort = phaseTimerPort;
        this.loadPersonPort = loadPersonPort;
        this.taskWriter = taskWriter;
        this.atRiskRule = atRiskRule;
        this.clock = clock;
    }

    public record InReview(Task task, PersonId reviewer, PhaseTimer openPhase, Instant now) {
        public PersonId assignee() {
            return task.assignee()
                    .orElseThrow(() -> new IllegalStateException(
                            "task " + task.id().value() + " reached review with nobody on it"));
        }
    }

    public InReview claim(TaskId id) {
        Instant now = clock.instant();
        PersonId caller = identifyCallerPort
                .currentCaller()
                .orElseThrow(() -> new NotAuthenticatedException("there is no session behind this call"));

        Task task = loadTaskPort
                .lockForTransition(id)
                .orElseThrow(() -> new TaskNotFoundException("there is no such task"));

        if (!reaches(caller, task)) {
            throw new TaskOutOfScopeException("this work belongs to somebody outside your team");
        }

        PhaseTimer open = phaseTimerPort.openPhaseOf(task.id()).orElse(null);

        return new InReview(task, caller, open, now);
    }

    public boolean reaches(PersonId caller, Task task) {
        return callerPermissionsPort.callerHolds(VIEW_ANY)
                || task.wasCreatedBy(caller)
                || task.isAssignedTo(caller)
                || task.assignee()
                        .map(assignee -> reportingLinePort.subtreeOf(caller).contains(assignee))
                        .orElse(false);
    }

    public TaskTransitionResult apply(TaskMove move) {
        taskWriter.writeMove(move);
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
