package com.flowops.task.application.shared;

import com.flowops.task.application.shared.exception.NotAuthenticatedException;
import com.flowops.task.application.shared.exception.NotTheCreatorException;
import com.flowops.task.application.shared.exception.TaskNotFoundException;
import com.flowops.task.application.shared.port.CallerPermissionsPort;
import com.flowops.task.application.shared.port.IdentifyCallerPort;
import com.flowops.task.application.shared.port.LoadTaskPort;
import com.flowops.task.application.shared.port.WorkspaceSettingsPort;
import com.flowops.task.domain.enums.TaskState;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.Task;
import com.flowops.task.domain.model.TaskId;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class TaskAuthorshipSupport {
    private static final String VIEW_ANY = "TASK_VIEW_ANY";

    private static final java.util.Set<TaskState> SETTLED =
            java.util.EnumSet.of(TaskState.COMPLETED, TaskState.APPROVED, TaskState.CLOSED);

    private final IdentifyCallerPort identifyCallerPort;
    private final CallerPermissionsPort callerPermissionsPort;
    private final LoadTaskPort loadTaskPort;
    private final WorkspaceSettingsPort workspaceSettingsPort;
    private final Clock clock;

    public TaskAuthorshipSupport(
            IdentifyCallerPort identifyCallerPort,
            CallerPermissionsPort callerPermissionsPort,
            LoadTaskPort loadTaskPort,
            WorkspaceSettingsPort workspaceSettingsPort,
            Clock clock) {
        this.identifyCallerPort = identifyCallerPort;
        this.callerPermissionsPort = callerPermissionsPort;
        this.loadTaskPort = loadTaskPort;
        this.workspaceSettingsPort = workspaceSettingsPort;
        this.clock = clock;
    }

    public record InHand(Task task, PersonId actor, Instant now) {}

    public InHand claim(TaskId id) {
        Instant now = clock.instant();
        PersonId caller = identifyCallerPort
                .currentCaller()
                .orElseThrow(() -> new NotAuthenticatedException("there is no session behind this call"));

        Task task = loadTaskPort
                .lockForTransition(id)
                .orElseThrow(() -> new TaskNotFoundException("there is no such task"));

        if (!directs(caller, task.creator())) {
            throw new NotTheCreatorException("only the person who assigned this work can change it");
        }

        return new InHand(task, caller, now);
    }

    public boolean directs(PersonId caller, PersonId creator) {
        return callerPermissionsPort.callerHolds(VIEW_ANY) || caller.equals(creator);
    }
}
