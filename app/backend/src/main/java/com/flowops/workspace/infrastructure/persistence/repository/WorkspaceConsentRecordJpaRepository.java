package com.flowops.workspace.infrastructure.persistence.repository;

import com.flowops.workspace.infrastructure.persistence.entity.WorkspaceConsentRecordJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceConsentRecordJpaRepository extends JpaRepository<WorkspaceConsentRecordJpaEntity, UUID> {
    Optional<WorkspaceConsentRecordJpaEntity> findFirstByUserIdOrderByAgreedAtDesc(UUID userId);
}
