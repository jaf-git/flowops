package com.flowops.workspace.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workspace_event")
public class WorkspaceEventJpaEntity {
    @Id
    private UUID id;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "subject_user_id")
    private UUID subjectUserId;

    @Column(name = "former_manager_user_id")
    private UUID formerManagerUserId;

    @Column(name = "new_manager_user_id")
    private UUID newManagerUserId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected WorkspaceEventJpaEntity() {}

    public WorkspaceEventJpaEntity(
            UUID id,
            String action,
            UUID actorUserId,
            UUID subjectUserId,
            UUID formerManagerUserId,
            UUID newManagerUserId,
            UUID workspaceId,
            Instant occurredAt) {
        this.id = id;
        this.action = action;
        this.actorUserId = actorUserId;
        this.subjectUserId = subjectUserId;
        this.formerManagerUserId = formerManagerUserId;
        this.newManagerUserId = newManagerUserId;
        this.workspaceId = workspaceId;
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public String getAction() {
        return action;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public UUID getSubjectUserId() {
        return subjectUserId;
    }

    public UUID getFormerManagerUserId() {
        return formerManagerUserId;
    }

    public UUID getNewManagerUserId() {
        return newManagerUserId;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
