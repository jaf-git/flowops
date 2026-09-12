package com.flowops.process.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "process_template")
public class ProcessTemplateJpaEntity {
    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "overview")
    private String overview;

    @Column(name = "author_user_id", nullable = false)
    private UUID authorUserId;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "trigger_note")
    private String triggerNote;

    @Column(name = "end_condition")
    private String endCondition;

    @Column(name = "owner_role")
    private String ownerRole;

    @Column(name = "status", nullable = false)
    private String status = "APPROVED";

    @Column(name = "origin", nullable = false)
    private String origin = "AUTHORED";

    protected ProcessTemplateJpaEntity() {}

    public String getTriggerNote() {
        return triggerNote;
    }

    public String getEndCondition() {
        return endCondition;
    }

    public String getOwnerRole() {
        return ownerRole;
    }

    public void describedBy(String triggerNote, String endCondition, String ownerRole) {
        this.triggerNote = triggerNote;
        this.endCondition = endCondition;
        this.ownerRole = ownerRole;
    }

    public ProcessTemplateJpaEntity(
            UUID id,
            UUID workspaceId,
            String name,
            String overview,
            UUID authorUserId,
            boolean active,
            Instant createdAt) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.name = name;
        this.overview = overview;
        this.authorUserId = authorUserId;
        this.active = active;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public String getName() {
        return name;
    }

    public String getOverview() {
        return overview;
    }

    public void setOverview(String overview) {
        this.overview = overview;
    }

    public UUID getAuthorUserId() {
        return authorUserId;
    }

    public void retire() {
        this.active = false;
    }

    public void composedFromDiscovery() {
        this.status = "DRAFT";
        this.origin = "COMPOSED_FROM_DISCOVERY";
    }

    public String getStatus() {
        return status;
    }

    public String getOrigin() {
        return origin;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
