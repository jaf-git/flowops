package com.flowops.task.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_link")
public class TaskLinkJpaEntity {
    @Id
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "url", nullable = false)
    private String url;

    @Column(name = "label")
    private String label;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "added_by_user_id", nullable = false)
    private UUID addedByUserId;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    protected TaskLinkJpaEntity() {}

    public TaskLinkJpaEntity(
            UUID id, UUID taskId, String url, String label, String role, UUID addedByUserId, Instant addedAt) {
        this.id = id;
        this.taskId = taskId;
        this.url = url;
        this.label = label;
        this.role = role;
        this.addedByUserId = addedByUserId;
        this.addedAt = addedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public String getUrl() {
        return url;
    }

    public String getLabel() {
        return label;
    }

    public String getRole() {
        return role;
    }

    public UUID getAddedByUserId() {
        return addedByUserId;
    }

    public Instant getAddedAt() {
        return addedAt;
    }
}
