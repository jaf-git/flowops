package com.flowops.task.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_checklist_item")
public class ChecklistItemJpaEntity {
    @Id
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "text", nullable = false)
    private String text;

    @Column(name = "done", nullable = false)
    private boolean done;

    @Column(name = "done_at")
    private Instant doneAt;

    @Column(name = "authored_by_user_id", nullable = false)
    private UUID authoredByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ChecklistItemJpaEntity() {}

    public ChecklistItemJpaEntity(
            UUID id,
            UUID taskId,
            int position,
            String text,
            boolean done,
            Instant doneAt,
            UUID authoredByUserId,
            Instant createdAt) {
        this.id = id;
        this.taskId = taskId;
        this.position = position;
        this.text = text;
        this.done = done;
        this.doneAt = doneAt;
        this.authoredByUserId = authoredByUserId;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public int getPosition() {
        return position;
    }

    public String getText() {
        return text;
    }

    public boolean isDone() {
        return done;
    }

    public Instant getDoneAt() {
        return doneAt;
    }

    public UUID getAuthoredByUserId() {
        return authoredByUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
