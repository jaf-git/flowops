package com.flowops.workspace.infrastructure.persistence.repository;

import com.flowops.workspace.infrastructure.persistence.entity.WorkspaceSettingsChangeJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceSettingsChangeJpaRepository extends JpaRepository<WorkspaceSettingsChangeJpaEntity, UUID> {}
