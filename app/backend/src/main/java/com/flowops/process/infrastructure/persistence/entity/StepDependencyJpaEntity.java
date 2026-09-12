package com.flowops.process.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "step_dependency")
@IdClass(StepDependencyJpaEntity.Key.class)
public class StepDependencyJpaEntity {
    @Id
    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Id
    @Column(name = "dependent_step_id", nullable = false)
    private UUID dependentStepId;

    @Id
    @Column(name = "depends_on_step_id", nullable = false)
    private UUID dependsOnStepId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "kind", nullable = false)
    private String kind;

    @Column(name = "confidence")
    private BigDecimal confidence;

    @Column(name = "confirmed_by")
    private UUID confirmedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    protected StepDependencyJpaEntity() {}

    public StepDependencyJpaEntity(
            UUID templateId,
            UUID dependentStepId,
            UUID dependsOnStepId,
            String kind,
            BigDecimal confidence,
            Instant createdAt) {
        this.templateId = templateId;
        this.dependentStepId = dependentStepId;
        this.dependsOnStepId = dependsOnStepId;
        this.kind = kind;
        this.confidence = confidence;
        this.createdAt = createdAt;
    }

    public void promotedBy(UUID person, Instant at) {
        this.kind = "CONFIRMED";
        this.confidence = null;
        this.confirmedBy = person;
        this.confirmedAt = at;
    }

    public String getKind() {
        return kind;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public UUID getConfirmedBy() {
        return confirmedBy;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public UUID getDependentStepId() {
        return dependentStepId;
    }

    public UUID getDependsOnStepId() {
        return dependsOnStepId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public static class Key implements Serializable {
        private UUID templateId;
        private UUID dependentStepId;
        private UUID dependsOnStepId;

        public Key() {}

        public Key(UUID templateId, UUID dependentStepId, UUID dependsOnStepId) {
            this.templateId = templateId;
            this.dependentStepId = dependentStepId;
            this.dependsOnStepId = dependsOnStepId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return Objects.equals(templateId, key.templateId)
                    && Objects.equals(dependentStepId, key.dependentStepId)
                    && Objects.equals(dependsOnStepId, key.dependsOnStepId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(templateId, dependentStepId, dependsOnStepId);
        }
    }
}
