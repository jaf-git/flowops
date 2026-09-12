package com.flowops.process.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "instance_step")
public class InstanceStepJpaEntity {
    @Id
    private UUID id;

    @Column(name = "instance_id", nullable = false)
    private UUID instanceId;

    @Column(name = "definition_id")
    private UUID definitionId;

    @Column(name = "task_template_id")
    private UUID taskTemplateId;

    @Column(name = "origin", nullable = false)
    private String origin;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "expected_duration_hours")
    private Integer expectedDurationHours;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "condition", nullable = false)
    private String condition;

    @Column(name = "task_id")
    private UUID taskId;

    @Column(name = "assignee_user_id")
    private UUID assigneeUserId;

    @Column(name = "reachable_at")
    private Instant reachableAt;

    @Column(name = "optional", nullable = false)
    private boolean optional;

    @Column(name = "condition_note")
    private String conditionNote;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "skipped_at")
    private Instant skippedAt;

    protected InstanceStepJpaEntity() {}

    public InstanceStepJpaEntity(
            UUID id,
            UUID instanceId,
            UUID definitionId,
            UUID taskTemplateId,
            String title,
            String description,
            Integer expectedDurationHours,
            int position,
            String condition,
            UUID taskId,
            UUID assigneeUserId,
            boolean optional,
            String conditionNote,
            Instant reachableAt,
            Instant closedAt,
            Instant skippedAt) {
        this.id = id;
        this.instanceId = instanceId;
        this.definitionId = definitionId;
        this.taskTemplateId = taskTemplateId;
        this.origin = definitionId == null ? "ATTACHED" : "DEFINITION";
        this.title = title;
        this.description = description;
        this.expectedDurationHours = expectedDurationHours;
        this.position = position;
        this.condition = condition;
        this.taskId = taskId;
        this.assigneeUserId = assigneeUserId;
        this.optional = optional;
        this.conditionNote = conditionNote;
        this.reachableAt = reachableAt;
        this.closedAt = closedAt;
        this.skippedAt = skippedAt;
    }

    public boolean isOptional() {
        return optional;
    }

    public String getConditionNote() {
        return conditionNote;
    }

    public Instant getSkippedAt() {
        return skippedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getInstanceId() {
        return instanceId;
    }

    public UUID getDefinitionId() {
        return definitionId;
    }

    public UUID getTaskTemplateId() {
        return taskTemplateId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Integer getExpectedDurationHours() {
        return expectedDurationHours;
    }

    public int getPosition() {
        return position;
    }

    public String getCondition() {
        return condition;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public UUID getAssigneeUserId() {
        return assigneeUserId;
    }

    public Instant getReachableAt() {
        return reachableAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void moveTo(
            String condition,
            UUID taskId,
            UUID assigneeUserId,
            Instant reachableAt,
            Instant closedAt,
            Instant skippedAt) {
        this.condition = condition;
        this.taskId = taskId;
        this.assigneeUserId = assigneeUserId;
        this.reachableAt = reachableAt;
        this.closedAt = closedAt;
        this.skippedAt = skippedAt;
    }

    public void moveTo(int position) {
        this.position = position;
    }

    public String getOrigin() {
        return origin;
    }
}
