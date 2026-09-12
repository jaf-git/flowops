package com.flowops.workspace.infrastructure.persistence.repository;

import com.flowops.workspace.infrastructure.persistence.entity.WorkspaceEventJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceEventJpaRepository extends JpaRepository<WorkspaceEventJpaEntity, UUID> {
    List<WorkspaceEventJpaEntity> findByActionAndSubjectUserIdOrderByOccurredAtAsc(String action, UUID subjectUserId);
}
