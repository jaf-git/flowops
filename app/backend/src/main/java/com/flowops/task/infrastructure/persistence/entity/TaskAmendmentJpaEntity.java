package com.flowops.task.infrastructure.persistence.entity;

import com.flowops.task.domain.enums.TaskPriority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_amendment")
public class TaskAmendmentJpaEntity {
    @Id
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "former_deadline")
    private Instant formerDeadline;

    @Column(name = "new_deadline")
    private Instant newDeadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "former_priority")
    private TaskPriority formerPriority;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_priority")
    private TaskPriority newPriority;

    @Column(name = "former_description")
    private String formerDescription;

    @Column(name = "new_description")
    private String newDescription;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected TaskAmendmentJpaEntity() {}

    public TaskAmendmentJpaEntity(
            UUID id,
            UUID taskId,
            UUID eventId,
            Instant formerDeadline,
            Instant newDeadline,
            TaskPriority formerPriority,
            TaskPriority newPriority,
            String formerDescription,
            String newDescription,
            UUID actorUserId,
            Instant occurredAt) {
        this.id = id;
        this.taskId = taskId;
        this.eventId = eventId;
        this.formerDeadline = formerDeadline;
        this.newDeadline = newDeadline;
        this.formerPriority = formerPriority;
        this.newPriority = newPriority;
        this.formerDescription = formerDescription;
        this.newDescription = newDescription;
        this.actorUserId = actorUserId;
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public Instant getFormerDeadline() {
        return formerDeadline;
    }

    public Instant getNewDeadline() {
        return newDeadline;
    }

    public TaskPriority getFormerPriority() {
        return formerPriority;
    }

    public TaskPriority getNewPriority() {
        return newPriority;
    }

    public String getFormerDescription() {
        return formerDescription;
    }

    public String getNewDescription() {
        return newDescription;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
