package com.flowops.task.infrastructure.persistence.repository;

import com.flowops.task.infrastructure.persistence.entity.ChecklistItemJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChecklistItemJpaRepository extends JpaRepository<ChecklistItemJpaEntity, UUID> {
    List<ChecklistItemJpaEntity> findByTaskIdOrderByPositionAsc(UUID taskId);

    Optional<ChecklistItemJpaEntity> findByTaskIdAndId(UUID taskId, UUID id);

    void deleteByTaskIdAndId(UUID taskId, UUID id);

    @Query("select coalesce(max(i.position), -1) + 1 from ChecklistItemJpaEntity i where i.taskId = :taskId")
    int nextPosition(@Param("taskId") UUID taskId);
}
