package com.flowops.task.infrastructure.persistence;

import com.flowops.task.application.shared.port.TaskMaterialPort;
import com.flowops.task.domain.enums.LinkRole;
import com.flowops.task.domain.model.ChecklistItem;
import com.flowops.task.domain.model.ChecklistItemId;
import com.flowops.task.domain.model.LinkUrl;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.TaskId;
import com.flowops.task.domain.model.TaskLink;
import com.flowops.task.domain.model.TaskLinkId;
import com.flowops.task.infrastructure.persistence.entity.ChecklistItemJpaEntity;
import com.flowops.task.infrastructure.persistence.entity.TaskLinkJpaEntity;
import com.flowops.task.infrastructure.persistence.repository.ChecklistItemJpaRepository;
import com.flowops.task.infrastructure.persistence.repository.TaskLinkJpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TaskMaterialPersistenceAdapter implements TaskMaterialPort {
    private final TaskLinkJpaRepository links;
    private final ChecklistItemJpaRepository items;

    public TaskMaterialPersistenceAdapter(TaskLinkJpaRepository links, ChecklistItemJpaRepository items) {
        this.links = links;
        this.items = items;
    }

    @Override
    public List<TaskLink> linksOf(TaskId task) {
        return links.findByTaskIdOrderByAddedAtAsc(task.value()).stream()
                .map(TaskMaterialPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public void attach(TaskLink link) {
        links.save(new TaskLinkJpaEntity(
                link.id().value(),
                link.task().value(),
                link.url().value(),
                link.label().orElse(null),
                link.role().name(),
                link.addedBy().value(),
                link.addedAt()));
    }

    @Override
    @Transactional
    public void detach(TaskId task, TaskLinkId link) {
        links.deleteByTaskIdAndId(task.value(), link.value());
    }

    @Override
    public Optional<TaskLink> findLink(TaskId task, TaskLinkId link) {
        return links.findByTaskIdAndId(task.value(), link.value()).map(TaskMaterialPersistenceAdapter::toDomain);
    }

    @Override
    public List<ChecklistItem> checklistOf(TaskId task) {
        return items.findByTaskIdOrderByPositionAsc(task.value()).stream()
                .map(TaskMaterialPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public void add(ChecklistItem item) {
        items.save(toEntity(item));
    }

    @Override
    public void update(ChecklistItem item) {
        items.save(toEntity(item));
    }

    @Override
    @Transactional
    public void remove(TaskId task, ChecklistItemId item) {
        items.deleteByTaskIdAndId(task.value(), item.value());
    }

    @Override
    public Optional<ChecklistItem> findItem(TaskId task, ChecklistItemId item) {
        return items.findByTaskIdAndId(task.value(), item.value()).map(TaskMaterialPersistenceAdapter::toDomain);
    }

    @Override
    public int nextPosition(TaskId task) {
        return items.nextPosition(task.value());
    }

    private static TaskLink toDomain(TaskLinkJpaEntity row) {
        return TaskLink.rebuild(
                TaskLinkId.of(row.getId()),
                TaskId.of(row.getTaskId()),
                LinkUrl.rebuild(row.getUrl()),
                row.getLabel(),
                LinkRole.valueOf(row.getRole()),
                PersonId.of(row.getAddedByUserId()),
                row.getAddedAt());
    }

    private static ChecklistItem toDomain(ChecklistItemJpaEntity row) {
        return ChecklistItem.rebuild(
                ChecklistItemId.of(row.getId()),
                TaskId.of(row.getTaskId()),
                row.getPosition(),
                row.getText(),
                row.isDone(),
                row.getDoneAt(),
                PersonId.of(row.getAuthoredByUserId()),
                row.getCreatedAt());
    }

    private static ChecklistItemJpaEntity toEntity(ChecklistItem item) {
        return new ChecklistItemJpaEntity(
                item.id().value(),
                item.task().value(),
                item.position(),
                item.text(),
                item.isDone(),
                item.doneAt().orElse(null),
                item.authoredBy().value(),
                item.createdAt());
    }
}
