package com.flowops.workspace.infrastructure.persistence.repository;

import com.flowops.workspace.infrastructure.persistence.entity.WorkspaceJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface WorkspaceJpaRepository extends JpaRepository<WorkspaceJpaEntity, UUID> {
    Optional<WorkspaceJpaEntity> findBySingletonTrue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WorkspaceJpaEntity w where w.singleton = true")
    Optional<WorkspaceJpaEntity> lockTheStructure();
}
