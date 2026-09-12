package com.flowops.task.application.shared;

import com.flowops.shared.marker.WorkMarkers;
import com.flowops.task.application.shared.port.WorkspaceSettingsPort;
import com.flowops.task.domain.enums.TaskState;
import com.flowops.task.domain.model.Task;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AtRiskRule {
    private static final Set<TaskState> SETTLED = Set.of(TaskState.COMPLETED, TaskState.APPROVED, TaskState.CLOSED);

    private final WorkspaceSettingsPort workspaceSettingsPort;
    private final Clock clock;

    public AtRiskRule(WorkspaceSettingsPort workspaceSettingsPort, Clock clock) {
        this.workspaceSettingsPort = workspaceSettingsPort;
        this.clock = clock;
    }

    public boolean of(Task task) {
        return of(task, task.deadline(), clock.instant());
    }

    public boolean of(Task task, Instant deadline, Instant now) {
        return WorkMarkers.atRisk(
                deadline,
                SETTLED.contains(task.state()),
                now,
                Duration.ofHours(workspaceSettingsPort.atRiskWindowHours()));
    }

    public boolean overdue(Task task) {
        return overdue(task, task.deadline(), clock.instant());
    }

    public boolean overdue(Task task, Instant deadline, Instant now) {
        return WorkMarkers.overdue(deadline, SETTLED.contains(task.state()), now);
    }
}
