package com.flowops.process.infrastructure.persistence.repository;

import com.flowops.process.infrastructure.persistence.entity.ProcessInstanceJpaEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProcessInstanceJpaRepository extends JpaRepository<ProcessInstanceJpaEntity, UUID> {
    @Query("select distinct i from ProcessInstanceJpaEntity i "
            + "where i.processOwnerUserId in :people "
            + "or exists (select 1 from InstanceStepJpaEntity s "
            + "           where s.instanceId = i.id and s.assigneeUserId in :people) "
            + "order by i.startedAt desc")
    List<ProcessInstanceJpaEntity> findInvolving(Collection<UUID> people);

    List<ProcessInstanceJpaEntity> findAllByOrderByStartedAtDesc();

    @Query("select distinct i from ProcessInstanceJpaEntity i "
            + "where i.archivedAt is null "
            + "and (i.processOwnerUserId in :people "
            + "     or exists (select 1 from InstanceStepJpaEntity s "
            + "                where s.instanceId = i.id and s.assigneeUserId in :people)) "
            + "order by i.startedAt desc")
    List<ProcessInstanceJpaEntity> findOnTheBoardInvolving(Collection<UUID> people);

    List<ProcessInstanceJpaEntity> findByArchivedAtIsNullOrderByStartedAtDesc();

    List<ProcessInstanceJpaEntity> findByStateOrderByStartedAtDesc(String state);
}
