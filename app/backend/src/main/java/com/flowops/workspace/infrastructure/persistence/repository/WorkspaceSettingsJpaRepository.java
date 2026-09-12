package com.flowops.workspace.infrastructure.persistence.repository;

import com.flowops.workspace.infrastructure.persistence.entity.WorkspaceSettingsJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceSettingsJpaRepository extends JpaRepository<WorkspaceSettingsJpaEntity, UUID> {
    Optional<WorkspaceSettingsJpaEntity> findByWorkspaceIdAndEffectiveToIsNull(UUID workspaceId);
}
