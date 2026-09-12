package com.flowops.task.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_event")
public class TaskEventJpaEntity {
    @Id
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "seq", insertable = false, updatable = false)
    private Long sequence;

    protected TaskEventJpaEntity() {}

    public TaskEventJpaEntity(UUID id, UUID taskId, String action, UUID actorUserId, Instant occurredAt) {
        this.id = id;
        this.taskId = taskId;
        this.action = action;
        this.actorUserId = actorUserId;
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public String getAction() {
        return action;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public Long getSequence() {
        return sequence;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
