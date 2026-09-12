package com.flowops.process.infrastructure.persistence.repository;

import com.flowops.process.infrastructure.persistence.entity.ProcessTemplateJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProcessTemplateJpaRepository extends JpaRepository<ProcessTemplateJpaEntity, UUID> {
    List<ProcessTemplateJpaEntity> findByActiveTrueOrderByCreatedAtDesc();

    @Query("select count(t) > 0 from ProcessTemplateJpaEntity t where t.active = true and lower(t.name) = lower(:name)")
    boolean existsActiveByName(String name);
}
