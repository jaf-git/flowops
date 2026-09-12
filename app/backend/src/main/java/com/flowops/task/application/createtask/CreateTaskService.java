package com.flowops.task.application.createtask;

import com.flowops.shared.event.FreeFormTaskCreated;
import com.flowops.task.application.shared.AtRiskRule;
import com.flowops.task.application.shared.TaskWriter;
import com.flowops.task.application.shared.exception.AssigneeNotActiveException;
import com.flowops.task.application.shared.exception.AssigneeOutOfScopeException;
import com.flowops.task.application.shared.exception.NotAuthenticatedException;
import com.flowops.task.application.shared.port.IdentifyCallerPort;
import com.flowops.task.application.shared.port.LoadPersonPort;
import com.flowops.task.application.shared.port.NotifyAssignmentPort;
import com.flowops.task.application.shared.port.ReportingLinePort;
import com.flowops.task.domain.enums.TaskPriority;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.Task;
import com.flowops.task.domain.model.TaskMove;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateTaskService implements CreateTaskUseCase {
    private final IdentifyCallerPort identifyCallerPort;
    private final LoadPersonPort loadPersonPort;
    private final ReportingLinePort reportingLinePort;
    private final TaskWriter taskWriter;
    private final NotifyAssignmentPort notifyAssignmentPort;
    private final AtRiskRule atRiskRule;
    private final ApplicationEventPublisher announcements;
    private final Clock clock;

    public CreateTaskService(
            IdentifyCallerPort identifyCallerPort,
            LoadPersonPort loadPersonPort,
            ReportingLinePort reportingLinePort,
            TaskWriter taskWriter,
            NotifyAssignmentPort notifyAssignmentPort,
            AtRiskRule atRiskRule,
            ApplicationEventPublisher announcements,
            Clock clock) {
        this.identifyCallerPort = identifyCallerPort;
        this.loadPersonPort = loadPersonPort;
        this.reportingLinePort = reportingLinePort;
        this.taskWriter = taskWriter;
        this.notifyAssignmentPort = notifyAssignmentPort;
        this.atRiskRule = atRiskRule;
        this.announcements = announcements;
        this.clock = clock;
    }

    private Task recordWhereItCameFrom(Task task, CreateTaskCommand command) {
        if ("TICKET".equals(command.kind())) {
            return task.asTicket();
        }
        if (command.templateId() == null) {
            return task;
        }
        return task.stampedFrom(command.templateId(), command.stampedEstimatedHours());
    }

    private void announceIfItNamesNoWork(Task task, Instant now) {
        if (task.provenance().isTicket() || task.provenance().template().isPresent()) {
            return;
        }
        announcements.publishEvent(new FreeFormTaskCreated(task.id().value(), now));
    }

    @Override
    @Transactional
    public CreateTaskResult execute(CreateTaskCommand command) {
        Instant now = clock.instant();
        PersonId creator = identifyCallerPort
                .currentCaller()
                .orElseThrow(() -> new NotAuthenticatedException("there is no session behind this call"));

        LoadPersonPort.Person assignee = loadPersonPort
                .describe(PersonId.of(command.assignee()))
                .filter(LoadPersonPort.Person::active)
                .orElseThrow(() -> new AssigneeNotActiveException("that person cannot be given work"));

        if (!reportingLinePort.isWithinScopeOf(creator, assignee.id())) {
            throw new AssigneeOutOfScopeException("that person is not yours to direct");
        }

        Task task = Task.given(
                command.title(),
                command.description(),
                assignee.id(),
                creator,
                command.deadline(),
                TaskPriority.valueOf(command.priority()),
                now);

        task = recordWhereItCameFrom(task, command);

        TaskMove move = TaskMove.creation(task, now);
        taskWriter.writeCreation(move);

        announceIfItNamesNoWork(task, now);

        notifyAssignmentPort.assignmentGiven(task.id(), assignee.id());

        return new CreateTaskResult(task, assignee.displayName(), atRiskRule.of(task));
    }
}
