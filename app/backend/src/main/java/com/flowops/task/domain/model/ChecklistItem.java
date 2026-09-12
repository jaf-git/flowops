package com.flowops.task.domain.model;

import com.flowops.task.domain.exception.ChecklistTextRequiredException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class ChecklistItem {
    private final ChecklistItemId id;
    private final TaskId task;
    private final int position;
    private final String text;
    private final boolean done;
    private final Instant doneAt;
    private final PersonId authoredBy;
    private final Instant createdAt;

    private ChecklistItem(
            ChecklistItemId id,
            TaskId task,
            int position,
            String text,
            boolean done,
            Instant doneAt,
            PersonId authoredBy,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.task = Objects.requireNonNull(task, "a step belongs to a task");
        this.position = position;
        this.authoredBy = Objects.requireNonNull(authoredBy, "somebody wrote it");
        this.createdAt = Objects.requireNonNull(createdAt);

        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            throw new ChecklistTextRequiredException();
        }
        this.text = trimmed;

        this.done = done;
        this.doneAt = done ? Objects.requireNonNull(doneAt, "a done step says when") : null;
    }

    public static ChecklistItem written(TaskId task, int position, String text, PersonId by, Instant at) {
        return new ChecklistItem(ChecklistItemId.generate(), task, position, text, false, null, by, at);
    }

    public static ChecklistItem rebuild(
            ChecklistItemId id,
            TaskId task,
            int position,
            String text,
            boolean done,
            Instant doneAt,
            PersonId by,
            Instant createdAt) {
        return new ChecklistItem(id, task, position, text, done, doneAt, by, createdAt);
    }

    public ChecklistItem ticked(boolean done, Instant at) {
        return new ChecklistItem(id, task, position, text, done, done ? at : null, authoredBy, createdAt);
    }

    public ChecklistItemId id() {
        return id;
    }

    public TaskId task() {
        return task;
    }

    public int position() {
        return position;
    }

    public String text() {
        return text;
    }

    public boolean isDone() {
        return done;
    }

    public Optional<Instant> doneAt() {
        return Optional.ofNullable(doneAt);
    }

    public PersonId authoredBy() {
        return authoredBy;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
