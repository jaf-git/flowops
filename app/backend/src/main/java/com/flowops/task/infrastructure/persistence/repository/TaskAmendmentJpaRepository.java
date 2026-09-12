package com.flowops.task.infrastructure.persistence.repository;

import com.flowops.task.infrastructure.persistence.entity.TaskAmendmentJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskAmendmentJpaRepository extends JpaRepository<TaskAmendmentJpaEntity, UUID> {
    java.util.List<TaskAmendmentJpaEntity> findByTaskIdOrderByOccurredAtDesc(UUID taskId);
}
