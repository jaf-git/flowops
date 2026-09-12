package com.flowops.task.application.viewtaskdetail;

import com.flowops.task.application.shared.AtRiskRule;
import com.flowops.task.application.shared.TaskReviewSupport;
import com.flowops.task.application.shared.exception.NotAuthenticatedException;
import com.flowops.task.application.shared.exception.TaskNotFoundException;
import com.flowops.task.application.shared.exception.TaskOutOfScopeException;
import com.flowops.task.application.shared.port.IdentifyCallerPort;
import com.flowops.task.application.shared.port.LoadApprovalPort;
import com.flowops.task.application.shared.port.LoadCompletionProofPort;
import com.flowops.task.application.shared.port.LoadDeadlineProposalPort;
import com.flowops.task.application.shared.port.LoadPersonPort;
import com.flowops.task.application.shared.port.LoadTaskPort;
import com.flowops.task.application.shared.port.PhaseTimerPort;
import com.flowops.task.domain.enums.PhaseKind;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.PhaseTimer;
import com.flowops.task.domain.model.Task;
import com.flowops.task.domain.model.TaskId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ViewTaskDetailService implements ViewTaskDetailUseCase {
    private final IdentifyCallerPort identifyCallerPort;
    private final LoadTaskPort loadTaskPort;
    private final PhaseTimerPort phaseTimerPort;
    private final LoadCompletionProofPort loadCompletionProofPort;
    private final LoadApprovalPort loadApprovalPort;
    private final LoadPersonPort loadPersonPort;
    private final LoadDeadlineProposalPort loadDeadlineProposalPort;
    private final TaskReviewSupport reviews;
    private final AtRiskRule atRiskRule;
    private final Clock clock;

    public ViewTaskDetailService(
            IdentifyCallerPort identifyCallerPort,
            LoadTaskPort loadTaskPort,
            PhaseTimerPort phaseTimerPort,
            LoadCompletionProofPort loadCompletionProofPort,
            LoadApprovalPort loadApprovalPort,
            LoadPersonPort loadPersonPort,
            LoadDeadlineProposalPort loadDeadlineProposalPort,
            TaskReviewSupport reviews,
            AtRiskRule atRiskRule,
            Clock clock) {
        this.identifyCallerPort = identifyCallerPort;
        this.loadTaskPort = loadTaskPort;
        this.phaseTimerPort = phaseTimerPort;
        this.loadCompletionProofPort = loadCompletionProofPort;
        this.loadApprovalPort = loadApprovalPort;
        this.loadPersonPort = loadPersonPort;
        this.loadDeadlineProposalPort = loadDeadlineProposalPort;
        this.reviews = reviews;
        this.atRiskRule = atRiskRule;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public ViewTaskDetailResult execute(TaskId task) {
        Instant now = clock.instant();
        PersonId caller = identifyCallerPort
                .currentCaller()
                .orElseThrow(() -> new NotAuthenticatedException("there is no session behind this call"));

        Task found = loadTaskPort.findById(task).orElseThrow(() -> new TaskNotFoundException("there is no such task"));
        if (!reviews.reaches(caller, found)) {
            throw new TaskOutOfScopeException("this work belongs to somebody outside your team");
        }

        List<PhaseTimer> phases = phaseTimerPort.allPhasesOf(task);
        PhaseTimer open = phases.stream().filter(PhaseTimer::isOpen).findFirst().orElse(null);

        return new ViewTaskDetailResult(
                found,
                found.assignee()
                        .flatMap(loadPersonPort::describe)
                        .map(LoadPersonPort.Person::displayName)
                        .orElse(""),
                open == null ? null : open.kind(),
                open == null ? null : open.startedAt(),
                spansOf(phases, now),
                loadCompletionProofPort.findByTask(task).orElse(null),
                loadApprovalPort.findByTask(task).orElse(null),
                lastSubmittedAt(phases),
                atRiskRule.of(found),
                atRiskRule.overdue(found),
                loadDeadlineProposalPort.openProposalOf(task).orElse(null));
    }

    private static List<ViewTaskDetailResult.PhaseSpan> spansOf(List<PhaseTimer> phases, Instant now) {
        Map<PhaseKind, Long> seconds = new EnumMap<>(PhaseKind.class);
        for (PhaseTimer phase : phases) {
            long elapsed = phase.elapsed()
                    .map(Duration::getSeconds)
                    .orElseGet(() -> Duration.between(phase.startedAt(), now).getSeconds());
            seconds.merge(phase.kind(), elapsed, Long::sum);
        }
        return seconds.entrySet().stream()
                .map(entry -> new ViewTaskDetailResult.PhaseSpan(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static Instant lastSubmittedAt(List<PhaseTimer> phases) {
        return phases.stream()
                .filter(phase -> phase.kind() == PhaseKind.REVIEW)
                .map(PhaseTimer::startedAt)
                .max(Instant::compareTo)
                .orElse(null);
    }
}
