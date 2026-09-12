package com.flowops.task.application.shared;

import com.flowops.task.application.shared.exception.NotAuthenticatedException;
import com.flowops.task.application.shared.exception.NotTheAssigneeException;
import com.flowops.task.application.shared.exception.TaskNotFoundException;
import com.flowops.task.application.shared.port.IdentifyCallerPort;
import com.flowops.task.application.shared.port.LoadPersonPort;
import com.flowops.task.application.shared.port.LoadTaskPort;
import com.flowops.task.application.shared.port.PhaseTimerPort;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.PhaseTimer;
import com.flowops.task.domain.model.Task;
import com.flowops.task.domain.model.TaskId;
import com.flowops.task.domain.model.TaskMove;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class TaskTransitionSupport {
    private final IdentifyCallerPort identifyCallerPort;
    private final LoadTaskPort loadTaskPort;
    private final PhaseTimerPort phaseTimerPort;
    private final LoadPersonPort loadPersonPort;
    private final TaskWriter taskWriter;
    private final AtRiskRule atRiskRule;
    private final Clock clock;

    public TaskTransitionSupport(
            IdentifyCallerPort identifyCallerPort,
            LoadTaskPort loadTaskPort,
            PhaseTimerPort phaseTimerPort,
            LoadPersonPort loadPersonPort,
            TaskWriter taskWriter,
            AtRiskRule atRiskRule,
            Clock clock) {
        this.identifyCallerPort = identifyCallerPort;
        this.loadTaskPort = loadTaskPort;
        this.phaseTimerPort = phaseTimerPort;
        this.loadPersonPort = loadPersonPort;
        this.taskWriter = taskWriter;
        this.atRiskRule = atRiskRule;
        this.clock = clock;
    }

    public record InFlight(Task task, PhaseTimer openPhase, Instant now) {
        public PersonId assignee() {
            return task.assignee()
                    .orElseThrow(() -> new IllegalStateException(
                            "task " + task.id().value() + " passed the assignee claim with nobody on it"));
        }
    }

    public InFlight claim(TaskId id) {
        Instant now = clock.instant();
        PersonId caller = identifyCallerPort
                .currentCaller()
                .orElseThrow(() -> new NotAuthenticatedException("there is no session behind this call"));

        Task task = loadTaskPort
                .lockForTransition(id)
                .orElseThrow(() -> new TaskNotFoundException("there is no such task"));

        if (!task.isAssignedTo(caller)) {
            throw new NotTheAssigneeException("this is not yours to move");
        }

        PhaseTimer open = phaseTimerPort
                .openPhaseOf(task.id())
                .orElseThrow(() -> new IllegalStateException("task " + task.id().value() + " has no open phase"));

        return new InFlight(task, open, now);
    }

    public void writeAmendment(TaskMove move) {
        taskWriter.writeAmendment(move);
    }

    public TaskTransitionResult resultOf(Task task, Instant deadline, Instant now) {
        return new TaskTransitionResult(task, nameOf(task), atRiskRule.of(task, deadline, now));
    }

    public TaskTransitionResult apply(TaskMove move) {
        taskWriter.writeMove(move);
        return new TaskTransitionResult(move.task(), nameOf(move.task()), atRiskRule.of(move.task()));
    }

    public TaskTransitionResult applyWithNewAssignee(TaskMove move) {
        taskWriter.writeStateAndAssignee(move);
        return new TaskTransitionResult(move.task(), nameOf(move.task()), atRiskRule.of(move.task()));
    }

    public TaskTransitionResult record(TaskMove move) {
        taskWriter.writeRecord(move);
        return new TaskTransitionResult(move.task(), nameOf(move.task()), atRiskRule.of(move.task()));
    }

    public String nameOf(Task task) {
        return task.assignee()
                .flatMap(loadPersonPort::describe)
                .map(LoadPersonPort.Person::displayName)
                .orElse("");
    }
}
