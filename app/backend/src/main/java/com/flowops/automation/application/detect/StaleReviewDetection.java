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
public class StaleReviewDetection implements Detection {
    private static final int MOST_LEVELS_CLIMBED = 24;

    private final TaskRiskReadPort tasks;
    private final ReportingLinePort reportingLine;

    public StaleReviewDetection(TaskRiskReadPort tasks, ReportingLinePort reportingLine) {
        this.tasks = tasks;
        this.reportingLine = reportingLine;
    }

    @Override
    public NotificationKind kind() {
        return NotificationKind.STALE_REVIEW;
    }

    @Override
    public List<Finding> evaluate(Instant now, Thresholds thresholds) {
        List<Finding> found = new ArrayList<>();
        for (TaskRiskReadPort.TaskInPhase waiting : tasks.awaitingReview()) {
            recipientFor(waiting, now, thresholds)
                    .map(reviewer -> new Finding(kind(), SubjectRef.task(waiting.id()), reviewer, waiting.openedAt()))
                    .ifPresent(found::add);
        }
        return List.copyOf(found);
    }

    private Optional<UUID> recipientFor(TaskRiskReadPort.TaskInPhase waiting, Instant now, Thresholds thresholds) {
        Duration twice = thresholds.review().multipliedBy(2);
        if (WorkMarkers.beyond(waiting.openedAt(), now, twice.plus(thresholds.review()), thresholds.calendar())) {
            return Optional.empty();
        }
        if (!WorkMarkers.beyond(waiting.openedAt(), now, thresholds.review(), thresholds.calendar())) {
            return Optional.empty();
        }
        Optional<UUID> reviewer = reviewerFor(waiting.assigner());
        boolean pastTwice = WorkMarkers.beyond(waiting.openedAt(), now, twice, thresholds.calendar());
        return pastTwice ? reviewer.flatMap(this::aboveTheReviewer) : reviewer;
    }

    private Optional<UUID> reviewerFor(UUID assigner) {
        UUID at = assigner;
        for (int climbed = 0; climbed < MOST_LEVELS_CLIMBED; climbed++) {
            Optional<UUID> reachable = reportingLine.reachable(at);
            if (reachable.isPresent()) {
                return reachable;
            }
            Optional<UUID> above = reportingLine.managerAbove(at);
            if (above.isEmpty()) {
                return Optional.empty();
            }
            at = above.get();
        }
        return Optional.empty();
    }

    private Optional<UUID> aboveTheReviewer(UUID reviewer) {
        return reportingLine.managerAbove(reviewer).flatMap(reportingLine::reachable);
    }
}
