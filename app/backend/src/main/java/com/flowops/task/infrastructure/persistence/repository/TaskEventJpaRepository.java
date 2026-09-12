package com.flowops.task.infrastructure.persistence.repository;

import com.flowops.task.infrastructure.persistence.entity.TaskEventJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TaskEventJpaRepository extends JpaRepository<TaskEventJpaEntity, UUID> {
    @Query("select coalesce(max(event.sequence), 0) from TaskEventJpaEntity event")
    long highestSequence();

    List<TaskEventRow> findBySequenceGreaterThanOrderBySequenceAsc(long sequence, Limit limit);

    interface TaskEventRow {
        long getSequence();

        UUID getTaskId();

        String getAction();

        Instant getOccurredAt();
    }
}
