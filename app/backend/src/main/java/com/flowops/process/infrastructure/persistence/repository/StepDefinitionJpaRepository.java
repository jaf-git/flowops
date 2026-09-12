package com.flowops.process.infrastructure.persistence.repository;

import com.flowops.process.infrastructure.persistence.entity.StepDefinitionJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StepDefinitionJpaRepository extends JpaRepository<StepDefinitionJpaEntity, UUID> {
    List<StepDefinitionJpaEntity> findByTemplateIdOrderByPosition(UUID templateId);
}
