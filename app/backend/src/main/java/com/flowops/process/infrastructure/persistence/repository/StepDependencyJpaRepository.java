package com.flowops.process.infrastructure.persistence.repository;

import com.flowops.process.infrastructure.persistence.entity.StepDependencyJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StepDependencyJpaRepository
        extends JpaRepository<StepDependencyJpaEntity, StepDependencyJpaEntity.Key> {
    List<StepDependencyJpaEntity> findByTemplateId(UUID templateId);

    List<StepDependencyJpaEntity> findByTemplateIdAndKind(UUID templateId, String kind);
}
