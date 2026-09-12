package com.flowops.task.infrastructure.persistence.repository;

import com.flowops.task.infrastructure.persistence.entity.TaskApprovalJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskApprovalJpaRepository extends JpaRepository<TaskApprovalJpaEntity, UUID> {
    Optional<TaskApprovalJpaEntity> findByTaskId(UUID taskId);
}
