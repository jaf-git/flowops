package com.flowops.task.infrastructure.persistence.repository;

import com.flowops.task.infrastructure.persistence.entity.TaskCompletionProofJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskCompletionProofJpaRepository extends JpaRepository<TaskCompletionProofJpaEntity, UUID> {
    Optional<TaskCompletionProofJpaEntity> findByTaskId(UUID taskId);
}
