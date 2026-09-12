package com.flowops.workspace.infrastructure.persistence.repository;

import com.flowops.workspace.infrastructure.persistence.entity.WorkspaceDataExportJpaEntity;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceDataExportJpaRepository extends JpaRepository<WorkspaceDataExportJpaEntity, UUID> {
    int countBySubjectUserIdAndProducedAtAfter(UUID subjectUserId, Instant since);
}
