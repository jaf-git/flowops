package com.flowops.automation.application.sweep;

import com.flowops.automation.application.detect.Detection;
import com.flowops.automation.application.detect.Finding;
import com.flowops.automation.application.escalate.EscalationLadder;
import com.flowops.automation.application.shared.port.AppendEventPort;
import com.flowops.automation.application.shared.port.WorkspaceThresholdPort;
import com.flowops.automation.application.shared.port.WorkspaceThresholdPort.Thresholds;
import com.flowops.automation.domain.AutomationAction;
import com.flowops.notification.application.published.NotifyUseCase;
import com.flowops.shared.notice.NotificationKind;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EvaluationPass {
    private static final Logger LOG = LoggerFactory.getLogger(EvaluationPass.class);

    private final List<Detection> detections;
    private final EscalationLadder ladder;
    private final WorkspaceThresholdPort workspace;
    private final NotifyUseCase notifications;
    private final AppendEventPort events;
    private final Clock clock;

    public EvaluationPass(
            List<Detection> detections,
            EscalationLadder ladder,
            WorkspaceThresholdPort workspace,
            NotifyUseCase notifications,
            AppendEventPort events,
            Clock clock) {
        this.detections = List.copyOf(detections);
        this.ladder = ladder;
        this.workspace = workspace;
        this.notifications = notifications;
        this.events = events;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${flowops.automation.evaluation-ms:300000}")
    public void evaluate() {
        Instant now = clock.instant();

        Optional<Thresholds> inForce = workspace.thresholds();

        if (inForce.isEmpty()) {
            LOG.debug("no workspace settings in force; nothing to evaluate against this pass");
            return;
        }

        Thresholds thresholds = inForce.get();

        for (Detection detection : detections) {
            try {
                raiseEverythingFoundBy(detection, now, thresholds);
            } catch (RuntimeException failure) {
                LOG.warn("the {} detection did not complete this pass", detection.kind(), failure);
            }
        }

        try {
            ladder.climb(now, thresholds);
        } catch (RuntimeException failure) {
            LOG.warn("the escalation ladder did not complete this pass", failure);
        }
    }

    private void raiseEverythingFoundBy(Detection detection, Instant now, Thresholds thresholds) {
        for (Finding finding : detection.evaluate(now, thresholds)) {
            events.recordOnce(eventFor(detection.kind()), finding.subject(), finding.episodeStartedAt(), now);

            notifications.raise(finding.asNotice());
        }
    }

    private static AutomationAction eventFor(NotificationKind kind) {
        return switch (kind) {
            case STEP_STALLED -> AutomationAction.STEP_STALL_DETECTED;
            case LONG_BLOCK_1, LONG_BLOCK_2 -> AutomationAction.LONG_BLOCK_DETECTED;
            case STALE_REVIEW -> AutomationAction.STALE_REVIEW_DETECTED;
            default -> throw new IllegalStateException(
                    kind + " is raised by a detection but names no event; AUTOMATION_03 §9 wants one");
        };
    }
}
