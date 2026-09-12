package com.flowops.automation.application.escalate;

import com.flowops.automation.application.shared.port.AppendEventPort;
import com.flowops.automation.application.shared.port.EscalationStatePort;
import com.flowops.automation.application.shared.port.ReportingLinePort;
import com.flowops.automation.application.shared.port.TaskRiskReadPort;
import com.flowops.automation.application.shared.port.TaskRiskReadPort.OverdueCandidate;
import com.flowops.automation.application.shared.port.WorkspaceThresholdPort.Thresholds;
import com.flowops.automation.domain.AutomationAction;
import com.flowops.automation.domain.EscalationEpisode;
import com.flowops.automation.domain.Rung;
import com.flowops.notification.application.published.NoticeRequest;
import com.flowops.notification.application.published.NotifyUseCase;
import com.flowops.shared.marker.WorkMarkers;
import com.flowops.shared.notice.NotificationKind;
import com.flowops.shared.notice.SubjectRef;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EscalationLadder {
    private static final Logger LOG = LoggerFactory.getLogger(EscalationLadder.class);

    private final TaskRiskReadPort tasks;
    private final ReportingLinePort reportingLine;
    private final EscalationStatePort episodes;
    private final NotifyUseCase notifications;
    private final AppendEventPort events;

    public EscalationLadder(
            TaskRiskReadPort tasks,
            ReportingLinePort reportingLine,
            EscalationStatePort episodes,
            NotifyUseCase notifications,
            AppendEventPort events) {
        this.tasks = tasks;
        this.reportingLine = reportingLine;
        this.episodes = episodes;
        this.notifications = notifications;
        this.events = events;
    }

    public void climb(Instant now, Thresholds thresholds) {
        for (OverdueCandidate candidate : tasks.escalationCandidates(now)) {
            try {
                consider(candidate, now, thresholds);
            } catch (RuntimeException failure) {
                LOG.warn("the ladder could not be evaluated for task {}", candidate.id(), failure);
            }
        }
    }

    private void consider(OverdueCandidate task, Instant now, Thresholds thresholds) {
        Optional<EscalationEpisode> live = episodes.openEpisode(task.id());

        if (!WorkMarkers.overdue(task.deadline(), task.settled(), now)) {
            live.filter(episode -> !firedAfter(episode, now)).ifPresent(episode -> episodes.resolve(episode.id(), now));
            return;
        }

        EscalationEpisode episode = live.orElseGet(() -> episodes.open(task.id(), task.deadline()));
        fireTheNextResolvableRung(episode, task, now, thresholds);
    }

    private static boolean firedAfter(EscalationEpisode episode, Instant now) {
        if (!episode.lastFiredAt().isAfter(now)) {
            return false;
        }
        LOG.warn(
                "escalation episode {} last fired at {}, which is after {} — the clock has moved backwards, "
                        + "so it is left live rather than resolved at an instant before it began",
                episode.id(),
                episode.lastFiredAt(),
                now);
        return true;
    }

    private void fireTheNextResolvableRung(
            EscalationEpisode episode, OverdueCandidate task, Instant now, Thresholds thresholds) {
        Optional<Rung> firstToTry = episode.rung().next();
        if (firstToTry.isEmpty()) {
            return;
        }
        Rung candidate = firstToTry.get();

        Optional<Duration> interval = thresholds.escalation().before(candidate);
        if (interval.isEmpty()) {
            return;
        }
        if (!thresholds.calendar().hasElapsed(episode.lastFiredAt(), now, interval.get())) {
            return;
        }

        Set<UUID> alreadyTold = everybodyToldSoFar(episode.rung(), task);

        while (true) {
            Optional<UUID> recipient = personAt(candidate, task).filter(person -> !alreadyTold.contains(person));
            if (recipient.isPresent()) {
                announce(episode, candidate, recipient.get(), task, now);
                return;
            }
            Optional<Rung> above = candidate.next();
            if (above.isEmpty()) {
                episodes.advance(episode, Rung.MANAGER_ABOVE_ASSIGNER, now);
                return;
            }
            candidate = above.get();
        }
    }

    private void announce(EscalationEpisode episode, Rung rung, UUID recipient, OverdueCandidate task, Instant now) {
        if (!episodes.advance(episode, rung, now)) {
            return;
        }

        events.record(AutomationAction.ESCALATION_ADVANCED, SubjectRef.task(task.id()), rung.index(), now);
        notifications.raise(new NoticeRequest(noticeFor(rung), recipient, SubjectRef.task(task.id())));
    }

    private Set<UUID> everybodyToldSoFar(Rung reached, OverdueCandidate task) {
        Set<UUID> told = new LinkedHashSet<>();
        for (Rung rung : Rung.values()) {
            if (rung == Rung.NONE || rung.index() > reached.index()) {
                continue;
            }
            personAt(rung, task).ifPresent(told::add);
        }
        return told;
    }

    private Optional<UUID> personAt(Rung rung, OverdueCandidate task) {
        return switch (rung) {
            case NONE -> Optional.empty();
            case ASSIGNEE -> reportingLine.reachable(task.assignee());
            case ASSIGNER -> reportingLine.reachable(task.assigner());
            case MANAGER_ABOVE_ASSIGNER -> reportingLine
                    .managerAbove(task.assigner())
                    .flatMap(reportingLine::reachable);
        };
    }

    private static NotificationKind noticeFor(Rung rung) {
        return switch (rung) {
            case ASSIGNEE -> NotificationKind.OVERDUE_RUNG_1;
            case ASSIGNER -> NotificationKind.OVERDUE_RUNG_2;
            case MANAGER_ABOVE_ASSIGNER -> NotificationKind.OVERDUE_RUNG_3;
            case NONE -> throw new IllegalStateException("rung 0 tells nobody and has no notice");
        };
    }
}
