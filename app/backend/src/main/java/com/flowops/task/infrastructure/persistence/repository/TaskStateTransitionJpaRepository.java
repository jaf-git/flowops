package com.flowops.task.infrastructure.persistence.repository;

import com.flowops.task.infrastructure.persistence.entity.TaskStateTransitionJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskStateTransitionJpaRepository extends JpaRepository<TaskStateTransitionJpaEntity, UUID> {
    java.util.Optional<TaskStateTransitionJpaEntity> findFirstByTaskIdAndToStateOrderByOccurredAtDesc(
            UUID taskId, String toState);

    java.util.List<TaskStateTransitionJpaEntity> findByTaskIdOrderByOccurredAtAsc(UUID taskId);
}
