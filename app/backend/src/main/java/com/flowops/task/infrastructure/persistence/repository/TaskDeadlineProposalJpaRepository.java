package com.flowops.task.infrastructure.persistence.repository;

import com.flowops.task.infrastructure.persistence.entity.TaskDeadlineProposalJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskDeadlineProposalJpaRepository extends JpaRepository<TaskDeadlineProposalJpaEntity, UUID> {
    Optional<TaskDeadlineProposalJpaEntity> findByTaskIdAndDecisionIsNull(UUID taskId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from TaskDeadlineProposalJpaEntity p where p.taskId = :taskId and p.decision is null")
    Optional<TaskDeadlineProposalJpaEntity> lockOpenByTaskId(@Param("taskId") UUID taskId);

    @Query("select p.taskId from TaskDeadlineProposalJpaEntity p where p.decision is null and p.taskId in :taskIds")
    List<UUID> openTaskIdsAmong(@Param("taskIds") Collection<UUID> taskIds);
}
