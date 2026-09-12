package com.flowops.process.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "process_instance")
public class ProcessInstanceJpaEntity {
    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "process_owner_user_id", nullable = false)
    private UUID processOwnerUserId;

    @Column(name = "started_by_user_id", nullable = false)
    private UUID startedByUserId;

    @Column(name = "state", nullable = false)
    private String state;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "abandoned_at")
    private Instant abandonedAt;

    @Column(name = "abandoned_reason")
    private String abandonedReason;

    @Column(name = "closure_note")
    private String closureNote;

    protected ProcessInstanceJpaEntity() {}

    public ProcessInstanceJpaEntity(
            UUID id,
            UUID workspaceId,
            String name,
            UUID templateId,
            UUID processOwnerUserId,
            UUID startedByUserId,
            String state,
            Instant startedAt,
            Instant completedAt) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.name = name;
        this.templateId = templateId;
        this.processOwnerUserId = processOwnerUserId;
        this.startedByUserId = startedByUserId;
        this.state = state;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public UUID getProcessOwnerUserId() {
        return processOwnerUserId;
    }

    public UUID getStartedByUserId() {
        return startedByUserId;
    }

    public String getState() {
        return state;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getAbandonedAt() {
        return abandonedAt;
    }

    public String getAbandonedReason() {
        return abandonedReason;
    }

    public void abandon(String state, Instant at, String reason) {
        this.state = state;
        this.abandonedAt = at;
        this.abandonedReason = reason;
    }

    public void archivedAt(Instant archivedAt) {
        this.archivedAt = archivedAt;
    }

    public Instant archivedAt() {
        return archivedAt;
    }

    public void moveTo(String state, Instant completedAt) {
        this.state = state;
        this.completedAt = completedAt;
    }

    public void closeEarly(String state, Instant completedAt, String note) {
        this.state = state;
        this.completedAt = completedAt;
        this.closureNote = note;
    }

    public String getClosureNote() {
        return closureNote;
    }
}
