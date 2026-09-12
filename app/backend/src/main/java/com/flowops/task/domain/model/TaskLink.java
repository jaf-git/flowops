package com.flowops.task.domain.model;

import com.flowops.task.domain.enums.LinkRole;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class TaskLink {
    private final TaskLinkId id;
    private final TaskId task;
    private final LinkUrl url;
    private final String label;
    private final LinkRole role;
    private final PersonId addedBy;
    private final Instant addedAt;

    private TaskLink(
            TaskLinkId id, TaskId task, LinkUrl url, String label, LinkRole role, PersonId addedBy, Instant addedAt) {
        this.id = Objects.requireNonNull(id);
        this.task = Objects.requireNonNull(task, "a link belongs to a task");
        this.url = Objects.requireNonNull(url);
        this.role = Objects.requireNonNull(role, "a link is an input, an output or a reference");
        this.addedBy = Objects.requireNonNull(addedBy, "somebody attached it");
        this.addedAt = Objects.requireNonNull(addedAt);

        String trimmed = label == null ? "" : label.trim();
        this.label = trimmed.isEmpty() ? null : trimmed;
    }

    public static TaskLink attached(TaskId task, LinkUrl url, String label, LinkRole role, PersonId by, Instant at) {
        return new TaskLink(TaskLinkId.generate(), task, url, label, role, by, at);
    }

    public static TaskLink rebuild(
            TaskLinkId id, TaskId task, LinkUrl url, String label, LinkRole role, PersonId by, Instant at) {
        return new TaskLink(id, task, url, label, role, by, at);
    }

    public String displayText() {
        return label == null ? url.host() : label;
    }

    public TaskLinkId id() {
        return id;
    }

    public TaskId task() {
        return task;
    }

    public LinkUrl url() {
        return url;
    }

    public Optional<String> label() {
        return Optional.ofNullable(label);
    }

    public LinkRole role() {
        return role;
    }

    public PersonId addedBy() {
        return addedBy;
    }

    public Instant addedAt() {
        return addedAt;
    }
}
