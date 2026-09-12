package com.flowops.process.infrastructure.persistence.repository;

import com.flowops.process.infrastructure.persistence.entity.InstanceStepJpaEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InstanceStepJpaRepository extends JpaRepository<InstanceStepJpaEntity, UUID> {
    List<InstanceStepJpaEntity> findByInstanceIdOrderByPosition(UUID instanceId);

    List<InstanceStepJpaEntity> findByInstanceIdInOrderByPosition(Collection<UUID> instanceIds);

    java.util.Optional<InstanceStepJpaEntity> findByTaskId(UUID taskId);

    boolean existsByTaskId(UUID taskId);

    @Query("select step.taskId from InstanceStepJpaEntity step where step.taskId in :taskIds")
    List<UUID> taskIdsAmong(@Param("taskIds") Collection<UUID> taskIds);
}
