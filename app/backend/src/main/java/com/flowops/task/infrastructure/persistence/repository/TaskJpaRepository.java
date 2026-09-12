package com.flowops.task.infrastructure.persistence.repository;

import com.flowops.task.infrastructure.persistence.entity.TaskJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskJpaRepository extends JpaRepository<TaskJpaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TaskJpaEntity t where t.id = :id")
    Optional<TaskJpaEntity> lockById(@Param("id") UUID id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update TaskJpaEntity t set t.state = :state where t.id = :id")
    void updateState(@Param("id") UUID id, @Param("state") String state);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update TaskJpaEntity t set t.state = :state, t.assigneeUserId = :assignee where t.id = :id")
    void updateStateAndAssignee(@Param("id") UUID id, @Param("state") String state, @Param("assignee") UUID assignee);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update TaskJpaEntity t set t.deadline = :deadline, t.priority = :priority,"
            + " t.description = :description, t.deadlineSetBy = :setBy, t.deadlineSetAt = :setAt,"
            + " t.deadlineAcknowledgedAt = :acknowledgedAt where t.id = :id")
    void updateFields(
            @Param("id") UUID id,
            @Param("deadline") java.time.Instant deadline,
            @Param("priority") String priority,
            @Param("description") String description,
            @Param("setBy") UUID setBy,
            @Param("setAt") java.time.Instant setAt,
            @Param("acknowledgedAt") java.time.Instant acknowledgedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update TaskJpaEntity t set t.deadline = :deadline, t.deadlineSetBy = :setBy,"
            + " t.deadlineSetAt = :setAt, t.deadlineAcknowledgedAt = null where t.id = :id")
    void updateDeadline(
            @Param("id") UUID id,
            @Param("deadline") java.time.Instant deadline,
            @Param("setBy") UUID setBy,
            @Param("setAt") java.time.Instant setAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update TaskJpaEntity t set t.deadlineAcknowledgedAt = :at where t.id = :id")
    void acknowledgeDeadline(@Param("id") UUID id, @Param("at") java.time.Instant at);

    @Query("select t from TaskJpaEntity t where t.creatorUserId = :creator"
            + " and t.deadlineSetBy is not null and t.deadlineSetBy = t.assigneeUserId"
            + " and t.deadlineAcknowledgedAt is null order by t.deadlineSetAt desc")
    List<TaskJpaEntity> findUnacknowledgedDeadlineNotices(@Param("creator") UUID creator);

    List<TaskJpaEntity> findByAssigneeUserIdInOrderByCreatedAtDesc(Collection<UUID> assignees);

    @Query("select t from TaskJpaEntity t where t.assigneeUserId in :assignees or t.creatorUserId = :creator"
            + " order by t.createdAt desc")
    List<TaskJpaEntity> findByAssigneeOrCreator(
            @Param("assignees") Collection<UUID> assignees, @Param("creator") UUID creator);

    List<TaskJpaEntity> findAllByOrderByCreatedAtDesc();

    List<TaskJpaEntity> findByStateAndAssigneeUserIdInOrderByCreatedAtAsc(String state, Collection<UUID> assignees);

    List<TaskJpaEntity> findByStateOrderByCreatedAtAsc(String state);
}
