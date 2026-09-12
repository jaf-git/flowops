package com.flowops.task.infrastructure.persistence.repository;

import com.flowops.task.infrastructure.persistence.entity.TaskPhaseTimerJpaEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskPhaseTimerJpaRepository extends JpaRepository<TaskPhaseTimerJpaEntity, UUID> {
    Optional<TaskPhaseTimerJpaEntity> findByTaskIdAndEndedAtIsNull(UUID taskId);

    List<TaskPhaseTimerJpaEntity> findByTaskIdInAndEndedAtIsNull(Collection<UUID> taskIds);

    List<TaskPhaseTimerJpaEntity> findByTaskIdOrderByStartedAtAsc(UUID taskId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update TaskPhaseTimerJpaEntity p set p.endedAt = :endedAt where p.id = :id and p.endedAt is null")
    int close(@Param("id") UUID id, @Param("endedAt") Instant endedAt);
}
