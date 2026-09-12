package com.flowops.task.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task")
public class TaskJpaEntity {
    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "assignee_user_id")
    private UUID assigneeUserId;

    @Column(name = "creator_user_id", nullable = false)
    private UUID creatorUserId;

    @Column(name = "deadline", nullable = false)
    private Instant deadline;

    @Column(name = "deadline_set_by")
    private UUID deadlineSetBy;

    @Column(name = "deadline_set_at")
    private Instant deadlineSetAt;

    @Column(name = "deadline_acknowledged_at")
    private Instant deadlineAcknowledgedAt;

    @Column(name = "priority", nullable = false)
    private String priority;

    @Column(name = "state", nullable = false)
    private String state;

    @Column(name = "kind", nullable = false)
    private String kind;

    @Column(name = "template_id")
    private UUID templateId;

    @Column(name = "stamped_estimated_hours")
    private java.math.BigDecimal stampedEstimatedHours;

    @Column(name = "self_assigned", nullable = false)
    private boolean selfAssigned;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "process_instance_id")
    private UUID processInstanceId;

    @Column(name = "instance_step_id")
    private UUID instanceStepId;

    @Column(name = "category_id")
    private UUID categoryId;

    protected TaskJpaEntity() {}

    public TaskJpaEntity(
            UUID id,
            UUID workspaceId,
            String title,
            String description,
            UUID assigneeUserId,
            UUID creatorUserId,
            Instant deadline,
            String priority,
            String state,
            boolean selfAssigned,
            Instant createdAt,
            String kind,
            UUID templateId,
            java.math.BigDecimal stampedEstimatedHours) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.title = title;
        this.description = description;
        this.assigneeUserId = assigneeUserId;
        this.creatorUserId = creatorUserId;
        this.deadline = deadline;
        this.priority = priority;
        this.state = state;
        this.selfAssigned = selfAssigned;
        this.createdAt = createdAt;
        this.kind = kind;
        this.templateId = templateId;
        this.stampedEstimatedHours = stampedEstimatedHours;
    }

    public UUID getId() {
        return id;
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

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public UUID getAssigneeUserId() {
        return assigneeUserId;
    }

    public UUID getCreatorUserId() {
        return creatorUserId;
    }

    public Instant getDeadline() {
        return deadline;
    }

    public String getPriority() {
        return priority;
    }

    public String getState() {
        return state;
    }

    public UUID getDeadlineSetBy() {
        return deadlineSetBy;
    }

    public Instant getDeadlineSetAt() {
        return deadlineSetAt;
    }

    public Instant getDeadlineAcknowledgedAt() {
        return deadlineAcknowledgedAt;
    }

    public boolean isSelfAssigned() {
        return selfAssigned;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getProcessInstanceId() {
        return processInstanceId;
    }

    public UUID getInstanceStepId() {
        return instanceStepId;
    }

    public void setProvenance(UUID processInstanceId, UUID instanceStepId) {
        this.processInstanceId = processInstanceId;
        this.instanceStepId = instanceStepId;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public java.math.BigDecimal getStampedEstimatedHours() {
        return stampedEstimatedHours;
    }

    public void setStampedEstimatedHours(java.math.BigDecimal stampedEstimatedHours) {
        this.stampedEstimatedHours = stampedEstimatedHours;
    }

    public void setTemplateId(UUID templateId) {
        this.templateId = templateId;
    }
}
