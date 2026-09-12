package com.flowops.task.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_state_transition")
public class TaskStateTransitionJpaEntity {
    @Id
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "from_state")
    private String fromState;

    @Column(name = "to_state", nullable = false)
    private String toState;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "reason")
    private String reason;

    @Column(name = "overridden", nullable = false)
    private boolean overridden;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected TaskStateTransitionJpaEntity() {}

    public TaskStateTransitionJpaEntity(
            UUID id,
            UUID taskId,
            String fromState,
            String toState,
            UUID actorUserId,
            String reason,
            boolean overridden,
            Instant occurredAt) {
        this.id = id;
        this.taskId = taskId;
        this.fromState = fromState;
        this.toState = toState;
        this.actorUserId = actorUserId;
        this.reason = reason;
        this.overridden = overridden;
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public String getFromState() {
        return fromState;
    }

    public String getToState() {
        return toState;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public String getReason() {
        return reason;
    }

    public boolean isOverridden() {
        return overridden;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
