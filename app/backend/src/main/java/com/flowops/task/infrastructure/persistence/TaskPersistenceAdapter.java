package com.flowops.task.infrastructure.persistence;

import com.flowops.task.application.shared.port.LoadDeadlineNoticesPort;
import com.flowops.task.application.shared.port.LoadTaskPort;
import com.flowops.task.application.shared.port.SaveTaskPort;
import com.flowops.task.domain.enums.TaskKind;
import com.flowops.task.domain.enums.TaskPriority;
import com.flowops.task.domain.enums.TaskState;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.Task;
import com.flowops.task.domain.model.TaskId;
import com.flowops.task.domain.model.TaskProvenance;
import com.flowops.task.infrastructure.persistence.entity.TaskJpaEntity;
import com.flowops.task.infrastructure.persistence.repository.TaskJpaRepository;
import com.flowops.workspace.application.describedirectory.DescribeDirectoryUseCase;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class TaskPersistenceAdapter implements SaveTaskPort, LoadTaskPort, LoadDeadlineNoticesPort {
    private final TaskJpaRepository tasks;
    private final DescribeDirectoryUseCase describeDirectoryUseCase;

    public TaskPersistenceAdapter(TaskJpaRepository tasks, DescribeDirectoryUseCase describeDirectoryUseCase) {
        this.tasks = tasks;
        this.describeDirectoryUseCase = describeDirectoryUseCase;
    }

    @Override
    public void createForProcess(Task task, java.util.UUID instanceId, java.util.UUID stepId) {
        TaskJpaEntity row = rowFor(task);
        row.setProvenance(instanceId, stepId);
        tasks.save(row);
    }

    @Override
    public void create(Task task) {
        tasks.save(rowFor(task));
    }

    @Override
    public void setProcessProvenance(java.util.UUID task, java.util.UUID instance, java.util.UUID step) {
        tasks.findById(task).ifPresent(row -> {
            row.setProvenance(instance, step);
            tasks.save(row);
        });
    }

    @Override
    public void setTemplateProvenance(java.util.UUID task, java.util.UUID template) {
        tasks.findById(task).ifPresent(row -> {
            row.setTemplateId(template);
            tasks.save(row);
        });
    }

    private TaskJpaEntity rowFor(Task task) {
        return new TaskJpaEntity(
                task.id().value(),
                describeDirectoryUseCase.currentWorkspaceId(),
                task.title(),
                task.description().orElse(null),
                task.assignee().map(PersonId::value).orElse(null),
                task.creator().value(),
                task.deadline(),
                task.priority().name(),
                task.state().name(),
                task.isSelfAssigned(),
                task.createdAt(),
                task.provenance().kind().name(),
                task.provenance().templateId(),
                task.provenance().stampedEstimatedHours());
    }

    @Override
    public void updateState(Task task) {
        tasks.updateState(task.id().value(), task.state().name());
    }

    @Override
    public void updateStateAndAssignee(Task task) {
        tasks.updateStateAndAssignee(
                task.id().value(),
                task.state().name(),
                task.assignee().map(PersonId::value).orElse(null));
    }

    @Override
    public void updateFields(Task task) {
        tasks.updateFields(
                task.id().value(),
                task.deadline(),
                task.priority().name(),
                task.description().orElse(null),
                task.deadlineSetBy().map(PersonId::value).orElse(null),
                task.deadlineSetAt().orElse(null),
                task.deadlineAcknowledgedAt().orElse(null));
    }

    @Override
    public List<LoadDeadlineNoticesPort.Notice> unacknowledgedFor(PersonId creator) {
        return tasks.findUnacknowledgedDeadlineNotices(creator.value()).stream()
                .map(row -> new LoadDeadlineNoticesPort.Notice(
                        TaskId.of(row.getId()),
                        row.getTitle(),
                        PersonId.of(row.getAssigneeUserId()),
                        row.getDeadline(),
                        row.getDeadlineSetAt()))
                .toList();
    }

    @Override
    public void updateDeadline(Task task) {
        tasks.updateDeadline(
                task.id().value(),
                task.deadline(),
                task.deadlineSetBy().map(PersonId::value).orElse(null),
                task.deadlineSetAt().orElse(null));
    }

    @Override
    public void acknowledgeDeadline(TaskId task, java.time.Instant at) {
        tasks.acknowledgeDeadline(task.value(), at);
    }

    @Override
    public Optional<Task> lockForTransition(TaskId id) {
        return tasks.lockById(id.value()).map(this::toDomain);
    }

    @Override
    public Optional<Task> findById(TaskId id) {
        return tasks.findById(id.value()).map(this::toDomain);
    }

    @Override
    public List<Task> findByAssignees(Collection<PersonId> assignees) {
        if (assignees.isEmpty()) {
            return List.of();
        }
        return tasks
                .findByAssigneeUserIdInOrderByCreatedAtDesc(
                        assignees.stream().map(PersonId::value).toList())
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Task> findByAssigneesOrCreator(Collection<PersonId> assignees, PersonId creator) {
        return tasks
                .findByAssigneeOrCreator(assignees.stream().map(PersonId::value).toList(), creator.value())
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Task> findAll() {
        return tasks.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Task> findCompletedByAssignees(Collection<PersonId> assignees) {
        if (assignees.isEmpty()) {
            return List.of();
        }
        return tasks
                .findByStateAndAssigneeUserIdInOrderByCreatedAtAsc(
                        TaskState.COMPLETED.name(),
                        assignees.stream().map(PersonId::value).toList())
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Task> findAllCompleted() {
        return tasks.findByStateOrderByCreatedAtAsc(TaskState.COMPLETED.name()).stream()
                .map(this::toDomain)
                .toList();
    }

    private Task toDomain(TaskJpaEntity entity) {
        return Task.rebuild(
                TaskId.of(entity.getId()),
                entity.getTitle(),
                entity.getDescription(),
                entity.getAssigneeUserId() == null ? null : PersonId.of(entity.getAssigneeUserId()),
                PersonId.of(entity.getCreatorUserId()),
                entity.getDeadline(),
                entity.getDeadlineSetBy() == null ? null : PersonId.of(entity.getDeadlineSetBy()),
                entity.getDeadlineSetAt(),
                entity.getDeadlineAcknowledgedAt(),
                TaskPriority.valueOf(entity.getPriority()),
                TaskState.valueOf(entity.getState()),
                entity.isSelfAssigned(),
                entity.getCreatedAt(),
                new TaskProvenance(
                        TaskKind.valueOf(entity.getKind()), entity.getTemplateId(), entity.getStampedEstimatedHours()));
    }
}
