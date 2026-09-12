package com.flowops.task.application.viewreviewqueue;

import com.flowops.task.application.shared.AtRiskRule;
import com.flowops.task.application.shared.exception.NotAuthenticatedException;
import com.flowops.task.application.shared.port.CallerPermissionsPort;
import com.flowops.task.application.shared.port.IdentifyCallerPort;
import com.flowops.task.application.shared.port.LoadDeadlineProposalPort;
import com.flowops.task.application.shared.port.LoadPersonPort;
import com.flowops.task.application.shared.port.LoadTaskPort;
import com.flowops.task.application.shared.port.PhaseTimerPort;
import com.flowops.task.application.shared.port.ReportingLinePort;
import com.flowops.task.application.shared.port.TaskCategoryFilingsPort;
import com.flowops.task.application.viewtasks.ViewTasksResult;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.PhaseTimer;
import com.flowops.task.domain.model.Task;
import com.flowops.task.domain.model.TaskId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ViewReviewQueueService implements ViewReviewQueueUseCase {
    private static final String VIEW_ANY = "TASK_VIEW_ANY";

    private final IdentifyCallerPort identifyCallerPort;
    private final CallerPermissionsPort callerPermissionsPort;
    private final ReportingLinePort reportingLinePort;
    private final LoadTaskPort loadTaskPort;
    private final PhaseTimerPort phaseTimerPort;
    private final LoadPersonPort loadPersonPort;
    private final LoadDeadlineProposalPort loadDeadlineProposalPort;
    private final TaskCategoryFilingsPort taskCategoryFilingsPort;
    private final AtRiskRule atRiskRule;

    public ViewReviewQueueService(
            IdentifyCallerPort identifyCallerPort,
            CallerPermissionsPort callerPermissionsPort,
            ReportingLinePort reportingLinePort,
            LoadTaskPort loadTaskPort,
            PhaseTimerPort phaseTimerPort,
            LoadPersonPort loadPersonPort,
            LoadDeadlineProposalPort loadDeadlineProposalPort,
            TaskCategoryFilingsPort taskCategoryFilingsPort,
            AtRiskRule atRiskRule) {
        this.taskCategoryFilingsPort = taskCategoryFilingsPort;
        this.atRiskRule = atRiskRule;
        this.identifyCallerPort = identifyCallerPort;
        this.callerPermissionsPort = callerPermissionsPort;
        this.reportingLinePort = reportingLinePort;
        this.loadTaskPort = loadTaskPort;
        this.phaseTimerPort = phaseTimerPort;
        this.loadPersonPort = loadPersonPort;
        this.loadDeadlineProposalPort = loadDeadlineProposalPort;
    }

    @Override
    @Transactional(readOnly = true)
    public ViewTasksResult execute() {
        PersonId caller = identifyCallerPort
                .currentCaller()
                .orElseThrow(() -> new NotAuthenticatedException("there is no session behind this call"));

        List<Task> waiting = completedWorkVisibleTo(caller);
        if (waiting.isEmpty()) {
            return new ViewTasksResult(List.of());
        }

        Map<TaskId, PhaseTimer> openPhases =
                phaseTimerPort.openPhasesOf(waiting.stream().map(Task::id).toList()).stream()
                        .collect(Collectors.toMap(PhaseTimer::task, Function.identity(), (first, second) -> first));

        Map<PersonId, String> names = loadPersonPort
                .describeAll(waiting.stream()
                        .map(Task::assignee)
                        .flatMap(java.util.Optional::stream)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(LoadPersonPort.Person::id, LoadPersonPort.Person::displayName));

        Set<TaskId> awaitingADecision = loadDeadlineProposalPort.withOpenProposalsAmong(
                waiting.stream().map(Task::id).toList());
        boolean seesEverything = callerPermissionsPort.callerHolds(VIEW_ANY);

        Map<java.util.UUID, TaskCategoryFilingsPort.Filing> filings = taskCategoryFilingsPort.filingsOf(
                waiting.stream().map(task -> task.id().value()).toList());

        return new ViewTasksResult(waiting.stream()
                .map(task ->
                        row(task, caller, openPhases.get(task.id()), names, awaitingADecision, seesEverything, filings))
                .toList());
    }

    private ViewTasksResult.Row row(
            Task task,
            PersonId caller,
            PhaseTimer openPhase,
            Map<PersonId, String> names,
            Set<TaskId> awaitingADecision,
            boolean seesEverything,
            Map<java.util.UUID, TaskCategoryFilingsPort.Filing> filings) {
        TaskCategoryFilingsPort.Filing filing = filings.get(task.id().value());
        return new ViewTasksResult.Row(
                task.id(),
                task.title(),
                task.assignee().map(PersonId::value).orElse(null),
                task.assignee()
                        .map(assignee -> names.getOrDefault(assignee, ""))
                        .orElse(""),
                task.deadline(),
                task.priority(),
                task.state(),
                openPhase == null ? null : openPhase.kind(),
                openPhase == null ? null : openPhase.startedAt(),
                task.isAssignedTo(caller),
                seesEverything || task.wasCreatedBy(caller),
                awaitingADecision.contains(task.id()),
                atRiskRule.of(task),
                atRiskRule.overdue(task),
                task.provenance().kind(),
                task.provenance().templateId(),
                filing == null ? null : filing.categoryId(),
                filing == null ? null : filing.categoryName());
    }

    private List<Task> completedWorkVisibleTo(PersonId caller) {
        if (callerPermissionsPort.callerHolds(VIEW_ANY)) {
            return loadTaskPort.findAllCompleted();
        }

        Set<PersonId> visible = new LinkedHashSet<>();
        visible.add(caller);
        visible.addAll(reportingLinePort.subtreeOf(caller));
        return loadTaskPort.findCompletedByAssignees(visible);
    }
}
