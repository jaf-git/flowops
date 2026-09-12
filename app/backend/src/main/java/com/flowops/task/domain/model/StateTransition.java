package com.flowops.task.domain.model;

import com.flowops.task.domain.enums.TaskState;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record StateTransition(
        StateTransitionId id,
        TaskId task,
        TaskState from,
        TaskState to,
        PersonId actor,
        String reason,
        boolean overridden,
        Instant occurredAt) {
    public StateTransition {
        Objects.requireNonNull(id);
        Objects.requireNonNull(task, "a transition belongs to a task");
        Objects.requireNonNull(to, "a transition arrives somewhere");
        Objects.requireNonNull(actor, "a transition was made by somebody");
        Objects.requireNonNull(occurredAt);
        reason = reason == null || reason.isBlank() ? null : reason.trim();
    }

    public static StateTransition creation(TaskId task, PersonId actor, Instant at) {
        return new StateTransition(StateTransitionId.generate(), task, null, TaskState.CREATED, actor, null, false, at);
    }

    public static StateTransition between(
            TaskId task, TaskState from, TaskState to, PersonId actor, String reason, Instant at) {
        return new StateTransition(
                StateTransitionId.generate(), task, Objects.requireNonNull(from), to, actor, reason, false, at);
    }

    public static StateTransition forced(
            TaskId task, TaskState from, TaskState to, PersonId owner, String reason, Instant at) {
        return new StateTransition(
                StateTransitionId.generate(), task, Objects.requireNonNull(from), to, owner, reason, true, at);
    }

    public Optional<TaskState> cameFrom() {
        return Optional.ofNullable(from);
    }

    public Optional<String> statedReason() {
        return Optional.ofNullable(reason);
    }
}
