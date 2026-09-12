package com.flowops.process.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "process_event")
public class ProcessEventJpaEntity {
    @Id
    private UUID id;

    @Column(name = "template_id")
    private UUID templateId;

    @Column(name = "instance_id")
    private UUID instanceId;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected ProcessEventJpaEntity() {}

    public ProcessEventJpaEntity(
            UUID id, UUID templateId, UUID instanceId, String action, UUID actorUserId, Instant occurredAt) {
        this.id = id;
        this.templateId = templateId;
        this.instanceId = instanceId;
        this.action = action;
        this.actorUserId = actorUserId;
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public UUID getInstanceId() {
        return instanceId;
    }

    public String getAction() {
        return action;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
