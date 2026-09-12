package com.flowops.process.infrastructure.persistence.repository;

import com.flowops.process.infrastructure.persistence.entity.InstanceStepDependencyJpaEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstanceStepDependencyJpaRepository
        extends JpaRepository<InstanceStepDependencyJpaEntity, InstanceStepDependencyJpaEntity.Key> {
    List<InstanceStepDependencyJpaEntity> findByInstanceId(java.util.UUID instanceId);

    List<InstanceStepDependencyJpaEntity> findByInstanceIdIn(Collection<java.util.UUID> instanceIds);

    void deleteByInstanceId(java.util.UUID instanceId);

    void deleteByDependentStepIdOrDependsOnStepId(java.util.UUID dependent, java.util.UUID dependsOn);
}
