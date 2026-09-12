package com.flowops.task.domain.model;

import com.flowops.task.domain.enums.TaskPriority;
import com.flowops.task.domain.exception.NothingChangedException;
import java.time.Instant;
import java.util.Optional;

public record TaskAmendment(
        TaskAmendmentId id,
        TaskId task,
        java.util.UUID event,
        Instant formerDeadline,
        Instant newDeadline,
        TaskPriority formerPriority,
        TaskPriority newPriority,
        String formerDescription,
        String newDescription,
        PersonId actor,
        Instant occurredAt) {
    public static TaskAmendment between(Task before, Task after, java.util.UUID event, PersonId actor, Instant at) {
        boolean deadlineMoved = !java.util.Objects.equals(before.deadline(), after.deadline());
        boolean priorityMoved = before.priority() != after.priority();
        String wasDescribed = before.description().orElse(null);
        String isDescribed = after.description().orElse(null);
        boolean descriptionMoved = !java.util.Objects.equals(wasDescribed, isDescribed);

        if (!deadlineMoved && !priorityMoved && !descriptionMoved) {
            throw new NothingChangedException();
        }

        return new TaskAmendment(
                TaskAmendmentId.generate(),
                after.id(),
                event,
                deadlineMoved ? before.deadline() : null,
                deadlineMoved ? after.deadline() : null,
                priorityMoved ? before.priority() : null,
                priorityMoved ? after.priority() : null,
                descriptionMoved ? wasDescribed : null,
                descriptionMoved ? isDescribed : null,
                actor,
                at);
    }

    public boolean deadlineMoved() {
        return newDeadline != null;
    }

    public Optional<String> descriptionBefore() {
        return Optional.ofNullable(formerDescription);
    }
}
