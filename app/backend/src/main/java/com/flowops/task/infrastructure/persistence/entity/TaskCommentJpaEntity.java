package com.flowops.task.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_comment")
public class TaskCommentJpaEntity {
    @Id
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "author_user_id", nullable = false)
    private UUID authorUserId;

    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TaskCommentJpaEntity() {}

    public TaskCommentJpaEntity(UUID id, UUID taskId, UUID authorUserId, String body, Instant createdAt) {
        this.id = id;
        this.taskId = taskId;
        this.authorUserId = authorUserId;
        this.body = body;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public UUID getAuthorUserId() {
        return authorUserId;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
