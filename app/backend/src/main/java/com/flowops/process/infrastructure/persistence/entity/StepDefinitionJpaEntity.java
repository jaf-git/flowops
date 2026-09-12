package com.flowops.process.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "step_definition")
public class StepDefinitionJpaEntity {
    @Id
    private UUID id;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "task_template_id", nullable = false)
    private UUID taskTemplateId;

    @Column(name = "expected_duration_hours")
    private Integer expectedDurationHours;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "optional", nullable = false)
    private boolean optional;

    @Column(name = "condition_note")
    private String conditionNote;

    protected StepDefinitionJpaEntity() {}

    public StepDefinitionJpaEntity(
            UUID id,
            UUID templateId,
            UUID taskTemplateId,
            Integer expectedDurationHours,
            int position,
            boolean optional,
            String conditionNote) {
        this.id = id;
        this.templateId = templateId;
        this.taskTemplateId = taskTemplateId;
        this.expectedDurationHours = expectedDurationHours;
        this.position = position;
        this.optional = optional;
        this.conditionNote = conditionNote;
    }

    public boolean isOptional() {
        return optional;
    }

    public String getConditionNote() {
        return conditionNote;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public UUID getTaskTemplateId() {
        return taskTemplateId;
    }

    public void setTaskTemplateId(UUID taskTemplateId) {
        this.taskTemplateId = taskTemplateId;
    }

    public Integer getExpectedDurationHours() {
        return expectedDurationHours;
    }

    public void setExpectedDurationHours(Integer expectedDurationHours) {
        this.expectedDurationHours = expectedDurationHours;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }
}
