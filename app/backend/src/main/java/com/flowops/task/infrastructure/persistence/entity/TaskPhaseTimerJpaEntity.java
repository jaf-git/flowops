package com.flowops.task.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_phase_timer")
public class TaskPhaseTimerJpaEntity {
    @Id
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "phase_kind", nullable = false)
    private String phaseKind;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected TaskPhaseTimerJpaEntity() {}

    public TaskPhaseTimerJpaEntity(UUID id, UUID taskId, String phaseKind, Instant startedAt, Instant endedAt) {
        this.id = id;
        this.taskId = taskId;
        this.phaseKind = phaseKind;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public String getPhaseKind() {
        return phaseKind;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }
}
