package com.flowops.task.application.createtask;

import com.flowops.task.application.shared.TaskWriter;
import com.flowops.task.application.shared.exception.AssigneeNotActiveException;
import com.flowops.task.application.shared.exception.AssigneeOutOfScopeException;
import com.flowops.task.application.shared.port.LoadPersonPort;
import com.flowops.task.application.shared.port.NotifyAssignmentPort;
import com.flowops.task.application.shared.port.ReportingLinePort;
import com.flowops.task.domain.enums.TaskPriority;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.Task;
import com.flowops.task.domain.model.TaskMove;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateProcessTaskService implements CreateProcessTaskUseCase {
    private final LoadPersonPort loadPersonPort;
    private final ReportingLinePort reportingLinePort;
    private final TaskWriter taskWriter;
    private final NotifyAssignmentPort notifyAssignmentPort;
    private final Clock clock;

    public CreateProcessTaskService(
            LoadPersonPort loadPersonPort,
            ReportingLinePort reportingLinePort,
            TaskWriter taskWriter,
            NotifyAssignmentPort notifyAssignmentPort,
            Clock clock) {
        this.loadPersonPort = loadPersonPort;
        this.reportingLinePort = reportingLinePort;
        this.taskWriter = taskWriter;
        this.notifyAssignmentPort = notifyAssignmentPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public UUID execute(NewProcessTask requested) {
        Instant now = clock.instant();
        PersonId creator = PersonId.of(requested.creatorId());

        LoadPersonPort.Person assignee = loadPersonPort
                .describe(PersonId.of(requested.assigneeId()))
                .filter(LoadPersonPort.Person::active)
                .orElseThrow(() -> new AssigneeNotActiveException("that person cannot be given work"));

        if (!reportingLinePort.isWithinScopeOf(creator, assignee.id())) {
            throw new AssigneeOutOfScopeException("that person is not yours to direct");
        }

        Task task = Task.given(
                requested.title(),
                requested.description(),
                assignee.id(),
                creator,
                requested.deadline(),
                TaskPriority.valueOf(requested.priority()),
                now);

        if (requested.taskTemplateId() != null) {
            task = task.stampedFrom(requested.taskTemplateId(), null);
        }

        taskWriter.writeProcessCreation(
                TaskMove.creation(task, now), requested.processInstanceId(), requested.instanceStepId());

        notifyAssignmentPort.assignmentGiven(task.id(), assignee.id());
        return task.id().value();
    }
}
