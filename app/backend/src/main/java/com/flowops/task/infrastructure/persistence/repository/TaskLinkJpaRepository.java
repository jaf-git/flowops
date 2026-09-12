package com.flowops.task.infrastructure.persistence.repository;

import com.flowops.task.infrastructure.persistence.entity.TaskLinkJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskLinkJpaRepository extends JpaRepository<TaskLinkJpaEntity, UUID> {
    List<TaskLinkJpaEntity> findByTaskIdOrderByAddedAtAsc(UUID taskId);

    Optional<TaskLinkJpaEntity> findByTaskIdAndId(UUID taskId, UUID id);

    void deleteByTaskIdAndId(UUID taskId, UUID id);
}
