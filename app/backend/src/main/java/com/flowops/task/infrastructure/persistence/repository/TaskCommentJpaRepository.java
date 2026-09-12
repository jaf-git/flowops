package com.flowops.task.infrastructure.persistence.repository;

import com.flowops.task.infrastructure.persistence.entity.TaskCommentJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskCommentJpaRepository extends JpaRepository<TaskCommentJpaEntity, UUID> {
    List<TaskCommentJpaEntity> findByTaskIdOrderByCreatedAtAsc(UUID taskId);
}
