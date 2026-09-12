package com.flowops.automation.application.detect;

import com.flowops.automation.application.shared.port.ReportingLinePort;
import com.flowops.automation.application.shared.port.TaskRiskReadPort;
import com.flowops.automation.application.shared.port.WorkspaceThresholdPort.Thresholds;
import com.flowops.shared.marker.WorkMarkers;
import com.flowops.shared.notice.NotificationKind;
import com.flowops.shared.notice.SubjectRef;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class LongBlockDetection implements Detection {
    private final TaskRiskReadPort tasks;
    private final ReportingLinePort reportingLine;

    public LongBlockDetection(TaskRiskReadPort tasks, ReportingLinePort reportingLine) {
        this.tasks = tasks;
        this.reportingLine = reportingLine;
    }

    @Override
    public NotificationKind kind() {
        return NotificationKind.LONG_BLOCK_1;
    }

    @Override
    public List<Finding> evaluate(Instant now, Thresholds thresholds) {
        List<Finding> found = new ArrayList<>();
        for (TaskRiskReadPort.TaskInPhase blocked : tasks.blocked()) {
            rungFor(blocked, now, thresholds).ifPresent(found::add);
        }
        return List.copyOf(found);
    }

    private Optional<Finding> rungFor(TaskRiskReadPort.TaskInPhase blocked, Instant now, Thresholds thresholds) {
        Duration twice = thresholds.block().multipliedBy(2);
        if (WorkMarkers.beyond(blocked.openedAt(), now, twice, thresholds.calendar())) {
            if (blocked.assigner().equals(blocked.assignee())) {
                return Optional.empty();
            }
            return reportingLine
                    .reachable(blocked.assigner())
                    .map(assigner ->
                            finding(NotificationKind.LONG_BLOCK_2, blocked.id(), assigner, blocked.openedAt()));
        }
        if (WorkMarkers.beyond(blocked.openedAt(), now, thresholds.block(), thresholds.calendar())) {
            return reportingLine
                    .reachable(blocked.assignee())
                    .map(assignee ->
                            finding(NotificationKind.LONG_BLOCK_1, blocked.id(), assignee, blocked.openedAt()));
        }
        return Optional.empty();
    }

    private Finding finding(NotificationKind rung, UUID taskId, UUID recipient, Instant episodeStartedAt) {
        return new Finding(rung, SubjectRef.task(taskId), recipient, episodeStartedAt);
    }
}
