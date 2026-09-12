package com.flowops.task.domain.model;

import com.flowops.task.domain.enums.PhaseKind;
import com.flowops.task.domain.enums.TaskAction;
import com.flowops.task.domain.enums.TaskState;
import com.flowops.task.domain.event.TaskEvent;
import com.flowops.task.domain.exception.BlockReasonRequiredException;
import com.flowops.task.domain.exception.OverrideReasonRequiredException;
import com.flowops.task.domain.exception.ReassignReasonRequiredException;
import com.flowops.task.domain.exception.RejectReasonRequiredException;
import com.flowops.task.domain.exception.ReworkReasonRequiredException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record TaskMove(Task task, PhaseTimer closed, PhaseTimer opened, StateTransition transition, TaskEvent event) {
    public TaskMove {
        Objects.requireNonNull(task);
        Objects.requireNonNull(transition, "a move is recorded or it did not happen");
        Objects.requireNonNull(event, "a move is logged or it did not happen");
    }

    public static TaskMove creation(Task created, Instant at) {
        PhaseKind first = created.state()
                .openPhase()
                .orElseThrow(() -> new IllegalStateException("a new task must occupy a phase"));
        return new TaskMove(
                created,
                null,
                PhaseTimer.opened(created.id(), first, at),
                StateTransition.creation(created.id(), created.creator(), at),
                TaskEvent.created(created.id(), created.creator(), at));
    }

    public static TaskMove acceptance(Task before, PhaseTimer openPhase, Instant at) {
        return moved(before, before.accepted(), openPhase, theAssignee(before), TaskAction.TASK_ACCEPTED, null, at);
    }

    public static TaskMove started(Task before, PhaseTimer openPhase, Instant at) {
        return moved(before, before.started(), openPhase, theAssignee(before), TaskAction.TASK_STARTED, null, at);
    }

    public static TaskMove blocked(Task before, PhaseTimer openPhase, String reason, Instant at) {
        if (reason == null || reason.isBlank()) {
            throw new BlockReasonRequiredException();
        }
        return moved(before, before.blocked(), openPhase, theAssignee(before), TaskAction.TASK_BLOCKED, reason, at);
    }

    public static TaskMove unblocked(Task before, PhaseTimer openPhase, String resolution, Instant at) {
        return moved(
                before, before.unblocked(), openPhase, theAssignee(before), TaskAction.TASK_UNBLOCKED, resolution, at);
    }

    public static TaskMove completed(Task before, PhaseTimer openPhase, Instant at) {
        return moved(before, before.completed(), openPhase, theAssignee(before), TaskAction.TASK_COMPLETED, null, at);
    }

    public static TaskMove approved(Task before, PhaseTimer openPhase, PersonId reviewer, Instant at) {
        return moved(before, before.approvedBy(reviewer), openPhase, reviewer, TaskAction.TASK_APPROVED, null, at);
    }

    public static TaskMove returnedForRework(
            Task before, PhaseTimer openPhase, PersonId reviewer, String reason, Instant at) {
        if (reason == null || reason.isBlank()) {
            throw new ReworkReasonRequiredException();
        }
        return moved(
                before,
                before.returnedForReworkBy(reviewer),
                openPhase,
                reviewer,
                TaskAction.TASK_RETURNED_FOR_REWORK,
                reason,
                at);
    }

    public static TaskMove closed(Task before, PhaseTimer openPhase, PersonId actor, Instant at) {
        return moved(before, before.closed(), openPhase, actor, TaskAction.TASK_CLOSED, null, at);
    }

    public static TaskMove rejected(Task before, PhaseTimer openPhase, String reason, Instant at) {
        if (reason == null || reason.isBlank()) {
            throw new RejectReasonRequiredException();
        }

        PersonId declinedBy = theAssignee(before);
        return moved(before, before.rejected(), openPhase, declinedBy, TaskAction.TASK_REJECTED, reason, at);
    }

    public static TaskMove reassigned(
            Task before, PhaseTimer openPhase, PersonId newAssignee, PersonId assigner, String reason, Instant at) {
        if (reason == null || reason.isBlank()) {
            throw new ReassignReasonRequiredException();
        }
        return moved(
                before, before.reassignedTo(newAssignee), openPhase, assigner, TaskAction.TASK_REASSIGNED, reason, at);
    }

    public static TaskMove overridden(
            Task before, PhaseTimer openPhase, TaskState target, PersonId owner, String reason, Instant at) {
        if (reason == null || reason.isBlank()) {
            throw new OverrideReasonRequiredException();
        }
        Task after = before.forcedTo(target);
        return new TaskMove(
                after,
                openPhase == null ? null : openPhase.closedAt(at),
                after.state()
                        .openPhase()
                        .map(kind -> PhaseTimer.opened(after.id(), kind, at))
                        .orElse(null),
                StateTransition.forced(after.id(), before.state(), after.state(), owner, reason, at),
                new TaskEvent(UUID.randomUUID(), after.id(), TaskAction.TASK_OVERRIDDEN, owner, at));
    }

    public static TaskEvent commented(TaskId task, PersonId author, Instant at) {
        return new TaskEvent(UUID.randomUUID(), task, TaskAction.TASK_COMMENTED, author, at);
    }

    public static TaskMove amended(
            Task before, Task after, PersonId actor, TaskAction action, String reason, Instant at) {
        return new TaskMove(
                after,
                null,
                null,
                StateTransition.between(after.id(), before.state(), after.state(), actor, reason, at),
                new TaskEvent(UUID.randomUUID(), after.id(), action, actor, at));
    }

    private static TaskMove moved(
            Task before,
            Task after,
            PhaseTimer openPhase,
            PersonId actor,
            TaskAction action,
            String reason,
            Instant at) {
        Objects.requireNonNull(openPhase, "a task in flight always has exactly one phase open");
        return new TaskMove(
                after,
                openPhase.closedAt(at),
                after.state()
                        .openPhase()
                        .map(kind -> PhaseTimer.opened(after.id(), kind, at))
                        .orElse(null),
                StateTransition.between(after.id(), before.state(), after.state(), actor, reason, at),
                new TaskEvent(UUID.randomUUID(), after.id(), action, actor, at));
    }

    private static PersonId theAssignee(Task task) {
        return task.assignee()
                .orElseThrow(() -> new IllegalStateException(
                        "task " + task.id().value() + " has nobody on it and only its assignee can move it"));
    }

    public Optional<PhaseTimer> closedPhase() {
        return Optional.ofNullable(closed);
    }

    public Optional<PhaseTimer> openedPhase() {
        return Optional.ofNullable(opened);
    }
}
