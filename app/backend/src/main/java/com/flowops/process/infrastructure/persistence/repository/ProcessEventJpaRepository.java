package com.flowops.process.infrastructure.persistence.repository;

import com.flowops.process.infrastructure.persistence.entity.ProcessEventJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessEventJpaRepository extends JpaRepository<ProcessEventJpaEntity, UUID> {}
