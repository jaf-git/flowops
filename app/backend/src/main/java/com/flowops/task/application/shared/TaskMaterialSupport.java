package com.flowops.task.application.shared;

import com.flowops.task.application.shared.exception.NotAuthenticatedException;
import com.flowops.task.application.shared.exception.NotTheAssigneeException;
import com.flowops.task.application.shared.exception.TaskNotFoundException;
import com.flowops.task.application.shared.port.IdentifyCallerPort;
import com.flowops.task.application.shared.port.LoadTaskPort;
import com.flowops.task.domain.enums.TaskState;
import com.flowops.task.domain.exception.TaskIsClosedException;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.Task;
import com.flowops.task.domain.model.TaskId;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class TaskMaterialSupport {
    private final IdentifyCallerPort identifyCallerPort;
    private final LoadTaskPort loadTaskPort;
    private final Clock clock;

    public TaskMaterialSupport(IdentifyCallerPort identifyCallerPort, LoadTaskPort loadTaskPort, Clock clock) {
        this.identifyCallerPort = identifyCallerPort;
        this.loadTaskPort = loadTaskPort;
        this.clock = clock;
    }

    public record InHand(Task task, PersonId actor, Instant now) {}

    public InHand claimForLinks(TaskId id) {
        InHand inHand = claim(id);
        if (!inHand.task().isAssignedTo(inHand.actor()) && !inHand.task().wasCreatedBy(inHand.actor())) {
            throw new NotTheAssigneeException("this is not your work and you did not give it out");
        }
        return inHand;
    }

    public InHand claimForTicking(TaskId id) {
        InHand inHand = claim(id);
        if (!inHand.task().isAssignedTo(inHand.actor())) {
            throw new NotTheAssigneeException("only the person doing the work says a step is done");
        }
        return inHand;
    }

    private InHand claim(TaskId id) {
        Instant now = clock.instant();
        PersonId caller = identifyCallerPort
                .currentCaller()
                .orElseThrow(() -> new NotAuthenticatedException("there is no session behind this call"));

        Task task = loadTaskPort.findById(id).orElseThrow(() -> new TaskNotFoundException("there is no such task"));

        if (task.state() == TaskState.CLOSED) {
            throw new TaskIsClosedException();
        }
        return new InHand(task, caller, now);
    }
}
