package com.flowops.task.domain.event;

import com.flowops.task.domain.enums.TaskAction;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.TaskId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TaskEvent(UUID id, TaskId task, TaskAction action, PersonId actor, Instant occurredAt) {
    public TaskEvent {
        Objects.requireNonNull(id);
        Objects.requireNonNull(task, "an event is about a task");
        Objects.requireNonNull(action, "an event records an action");
        Objects.requireNonNull(actor, "an event names who acted");
        Objects.requireNonNull(occurredAt);
    }

    public static TaskEvent created(TaskId task, PersonId actor, Instant at) {
        return new TaskEvent(UUID.randomUUID(), task, TaskAction.TASK_CREATED, actor, at);
    }

    public static TaskEvent accepted(TaskId task, PersonId actor, Instant at) {
        return new TaskEvent(UUID.randomUUID(), task, TaskAction.TASK_ACCEPTED, actor, at);
    }

    public static TaskEvent linkAttached(TaskId task, PersonId actor, Instant at) {
        return new TaskEvent(UUID.randomUUID(), task, TaskAction.LINK_ATTACHED, actor, at);
    }

    public static TaskEvent linkDetached(TaskId task, PersonId actor, Instant at) {
        return new TaskEvent(UUID.randomUUID(), task, TaskAction.LINK_DETACHED, actor, at);
    }

    public static TaskEvent checklistItemAdded(TaskId task, PersonId actor, Instant at) {
        return new TaskEvent(UUID.randomUUID(), task, TaskAction.CHECKLIST_ITEM_ADDED, actor, at);
    }

    public static TaskEvent checklistItemTicked(TaskId task, PersonId actor, Instant at) {
        return new TaskEvent(UUID.randomUUID(), task, TaskAction.CHECKLIST_ITEM_TICKED, actor, at);
    }

    public static TaskEvent checklistItemRemoved(TaskId task, PersonId actor, Instant at) {
        return new TaskEvent(UUID.randomUUID(), task, TaskAction.CHECKLIST_ITEM_REMOVED, actor, at);
    }
}
