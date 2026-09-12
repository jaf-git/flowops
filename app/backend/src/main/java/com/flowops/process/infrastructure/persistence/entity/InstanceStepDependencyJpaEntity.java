package com.flowops.process.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "instance_step_dependency")
@IdClass(InstanceStepDependencyJpaEntity.Key.class)
public class InstanceStepDependencyJpaEntity {
    @Id
    @Column(name = "instance_id", nullable = false)
    private UUID instanceId;

    @Id
    @Column(name = "dependent_step_id", nullable = false)
    private UUID dependentStepId;

    @Id
    @Column(name = "depends_on_step_id", nullable = false)
    private UUID dependsOnStepId;

    protected InstanceStepDependencyJpaEntity() {}

    public InstanceStepDependencyJpaEntity(UUID instanceId, UUID dependentStepId, UUID dependsOnStepId) {
        this.instanceId = instanceId;
        this.dependentStepId = dependentStepId;
        this.dependsOnStepId = dependsOnStepId;
    }

    public UUID getInstanceId() {
        return instanceId;
    }

    public UUID getDependentStepId() {
        return dependentStepId;
    }

    public UUID getDependsOnStepId() {
        return dependsOnStepId;
    }

    public static class Key implements Serializable {
        private UUID instanceId;
        private UUID dependentStepId;
        private UUID dependsOnStepId;

        public Key() {}

        public Key(UUID instanceId, UUID dependentStepId, UUID dependsOnStepId) {
            this.instanceId = instanceId;
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
            return Objects.equals(instanceId, key.instanceId)
                    && Objects.equals(dependentStepId, key.dependentStepId)
                    && Objects.equals(dependsOnStepId, key.dependsOnStepId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(instanceId, dependentStepId, dependsOnStepId);
        }
    }
}
