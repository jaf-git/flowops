package com.flowops.task.domain.model;

import com.flowops.task.domain.enums.TaskPriority;
import com.flowops.task.domain.enums.TaskState;
import com.flowops.task.domain.exception.CannotReviewOwnWorkException;
import com.flowops.task.domain.exception.DeadlineInThePastException;
import com.flowops.task.domain.exception.DeadlineRequiredException;
import com.flowops.task.domain.exception.DeadlineRequiredToStartException;
import com.flowops.task.domain.exception.IllegalTransitionException;
import com.flowops.task.domain.exception.NothingChangedException;
import com.flowops.task.domain.exception.TaskIsClosedException;
import com.flowops.task.domain.exception.TaskTitleRequiredException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class Task {
    private final TaskId id;
    private final String title;
    private final String description;
    private final PersonId assignee;
    private final PersonId creator;
    private final Instant deadline;
    private final PersonId deadlineSetBy;
    private final Instant deadlineSetAt;
    private final Instant deadlineAcknowledgedAt;
    private final TaskPriority priority;
    private final TaskState state;
    private final boolean selfAssigned;
    private final Instant createdAt;
    private final TaskProvenance provenance;

    private Task(
            TaskId id,
            String title,
            String description,
            PersonId assignee,
            PersonId creator,
            Instant deadline,
            PersonId deadlineSetBy,
            Instant deadlineSetAt,
            Instant deadlineAcknowledgedAt,
            TaskPriority priority,
            TaskState state,
            boolean selfAssigned,
            Instant createdAt,
            TaskProvenance provenance) {
        this.id = Objects.requireNonNull(id);
        this.provenance = Objects.requireNonNull(provenance, "a task knows where it came from");

        this.assignee = assignee;
        this.creator = Objects.requireNonNull(creator, "a task is given by somebody");
        this.priority = Objects.requireNonNull(priority);
        this.state = Objects.requireNonNull(state);
        this.createdAt = Objects.requireNonNull(createdAt);

        String trimmed = title == null ? "" : title.trim();
        if (trimmed.isEmpty()) {
            throw new TaskTitleRequiredException();
        }
        this.title = trimmed;
        this.description = description == null || description.isBlank() ? null : description.trim();

        this.deadline = deadline;
        this.deadlineSetBy = deadlineSetBy;
        this.deadlineSetAt = deadlineSetAt;
        this.deadlineAcknowledgedAt = deadlineAcknowledgedAt;
        this.selfAssigned = selfAssigned;
    }

    public static Task given(
            String title,
            String description,
            PersonId assignee,
            PersonId creator,
            Instant deadline,
            TaskPriority priority,
            Instant now) {
        if (deadline != null && !deadline.isAfter(now)) {
            throw new DeadlineInThePastException();
        }

        Objects.requireNonNull(assignee, "a task is given to somebody");
        return new Task(
                TaskId.generate(),
                title,
                description,
                assignee,
                creator,
                deadline,
                null,
                null,
                null,
                priority,
                TaskState.CREATED,
                assignee.equals(creator),
                now,
                TaskProvenance.freeform());
    }

    public static Task rebuild(
            TaskId id,
            String title,
            String description,
            PersonId assignee,
            PersonId creator,
            Instant deadline,
            PersonId deadlineSetBy,
            Instant deadlineSetAt,
            Instant deadlineAcknowledgedAt,
            TaskPriority priority,
            TaskState state,
            boolean selfAssigned,
            Instant createdAt,
            TaskProvenance provenance) {
        return new Task(
                id,
                title,
                description,
                assignee,
                creator,
                deadline,
                deadlineSetBy,
                deadlineSetAt,
                deadlineAcknowledgedAt,
                priority,
                state,
                selfAssigned,
                createdAt,
                provenance);
    }

    public Task accepted() {
        return withState(state.moveTo(TaskState.ACCEPTED));
    }

    public Task rejected() {
        return new Task(
                id,
                title,
                description,
                null,
                creator,
                deadline,
                deadlineSetBy,
                deadlineSetAt,
                deadlineAcknowledgedAt,
                priority,
                state.moveTo(TaskState.CREATED),
                selfAssigned,
                createdAt,
                provenance);
    }

    public Task amended(Instant deadline, TaskPriority priority, String description, Instant now) {
        if (state == TaskState.CLOSED) {
            throw new TaskIsClosedException();
        }
        if (deadline == null) {
            throw new DeadlineRequiredException();
        }
        if (!deadline.isAfter(now)) {
            throw new DeadlineInThePastException();
        }

        String wanted = description == null || description.isBlank() ? null : description.trim();
        if (deadline.equals(this.deadline) && priority == this.priority && Objects.equals(wanted, this.description)) {
            throw new NothingChangedException();
        }

        return new Task(
                id,
                title,
                wanted,
                assignee,
                creator,
                deadline,
                deadlineSetBy,
                deadlineSetAt,
                deadlineAcknowledgedAt,
                priority,
                state,
                selfAssigned,
                createdAt,
                provenance);
    }

    public Task withDeadline(Instant deadline, PersonId setBy, Instant setAt) {
        if (deadline == null) {
            throw new DeadlineRequiredException();
        }
        if (!deadline.isAfter(setAt)) {
            throw new DeadlineInThePastException();
        }

        return new Task(
                id,
                title,
                description,
                assignee,
                creator,
                deadline,
                setBy,
                setAt,
                null,
                priority,
                state,
                selfAssigned,
                createdAt,
                provenance);
    }

    public Optional<PersonId> deadlineSetBy() {
        return Optional.ofNullable(deadlineSetBy);
    }

    public Optional<Instant> deadlineSetAt() {
        return Optional.ofNullable(deadlineSetAt);
    }

    public Optional<Instant> deadlineAcknowledgedAt() {
        return Optional.ofNullable(deadlineAcknowledgedAt);
    }

    public Task deadlineAcknowledgedAt(Instant at) {
        return new Task(
                id,
                title,
                description,
                assignee,
                creator,
                deadline,
                deadlineSetBy,
                deadlineSetAt,
                at,
                priority,
                state,
                selfAssigned,
                createdAt,
                provenance);
    }

    public Task started() {
        if (deadline == null) {
            throw new DeadlineRequiredToStartException();
        }
        return withState(state.moveTo(TaskState.IN_PROGRESS));
    }

    public Task blocked() {
        return withState(state.moveTo(TaskState.BLOCKED));
    }

    public Task unblocked() {
        return withState(state.moveTo(TaskState.IN_PROGRESS));
    }

    public Task completed() {
        return withState(state.moveTo(TaskState.COMPLETED));
    }

    public Task approvedBy(PersonId reviewer) {
        refuseSelfReview(reviewer);
        return withState(state.moveTo(TaskState.APPROVED));
    }

    public Task returnedForReworkBy(PersonId reviewer) {
        refuseSelfReview(reviewer);
        return withState(state.moveTo(TaskState.IN_PROGRESS));
    }

    public Task closed() {
        return withState(state.moveTo(TaskState.CLOSED));
    }

    public Task reassignedTo(PersonId newAssignee) {
        Objects.requireNonNull(newAssignee, "reassignment moves work to somebody");
        if (state == TaskState.CLOSED) {
            throw new TaskIsClosedException();
        }
        if (state == TaskState.COMPLETED) {
            throw new IllegalTransitionException(state, TaskState.CREATED);
        }
        if (newAssignee.equals(assignee)) {
            throw new NothingChangedException();
        }
        return new Task(
                id,
                title,
                description,
                newAssignee,
                creator,
                deadline,
                deadlineSetBy,
                deadlineSetAt,
                deadlineAcknowledgedAt,
                priority,
                TaskState.CREATED,
                selfAssigned,
                createdAt,
                provenance);
    }

    public Task forcedTo(TaskState target) {
        Objects.requireNonNull(target, "an override arrives somewhere");
        if (target == state) {
            throw new NothingChangedException();
        }
        return withState(target);
    }

    private void refuseSelfReview(PersonId reviewer) {
        if (isAssignedTo(reviewer)) {
            throw new CannotReviewOwnWorkException();
        }
    }

    public boolean isAssignedTo(PersonId person) {
        return assignee != null && assignee.equals(person);
    }

    public boolean wasCreatedBy(PersonId person) {
        return creator.equals(person);
    }

    public Task stampedFrom(java.util.UUID templateId, java.math.BigDecimal stampedEstimatedHours) {
        return withProvenance(TaskProvenance.fromTemplate(templateId, stampedEstimatedHours));
    }

    public Task asTicket() {
        return withProvenance(TaskProvenance.ticket());
    }

    private Task withProvenance(TaskProvenance next) {
        return new Task(
                id,
                title,
                description,
                assignee,
                creator,
                deadline,
                deadlineSetBy,
                deadlineSetAt,
                deadlineAcknowledgedAt,
                priority,
                state,
                selfAssigned,
                createdAt,
                next);
    }

    public TaskProvenance provenance() {
        return provenance;
    }

    private Task withState(TaskState next) {
        return new Task(
                id,
                title,
                description,
                assignee,
                creator,
                deadline,
                deadlineSetBy,
                deadlineSetAt,
                deadlineAcknowledgedAt,
                priority,
                next,
                selfAssigned,
                createdAt,
                provenance);
    }

    public TaskId id() {
        return id;
    }

    public String title() {
        return title;
    }

    public Optional<String> description() {
        return Optional.ofNullable(description);
    }

    public Optional<PersonId> assignee() {
        return Optional.ofNullable(assignee);
    }

    public PersonId creator() {
        return creator;
    }

    public Instant deadline() {
        return deadline;
    }

    public TaskPriority priority() {
        return priority;
    }

    public TaskState state() {
        return state;
    }

    public boolean isSelfAssigned() {
        return selfAssigned;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
