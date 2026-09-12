package com.flowops.task.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_approval")
public class TaskApprovalJpaEntity {
    @Id
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(nullable = false)
    private short score;

    @Column(name = "comment")
    private String comment;

    @Column(name = "reviewer_user_id", nullable = false)
    private UUID reviewerUserId;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    protected TaskApprovalJpaEntity() {}

    public TaskApprovalJpaEntity(
            UUID id, UUID taskId, short score, String comment, UUID reviewerUserId, Instant decidedAt) {
        this.id = id;
        this.taskId = taskId;
        this.score = score;
        this.comment = comment;
        this.reviewerUserId = reviewerUserId;
        this.decidedAt = decidedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public short getScore() {
        return score;
    }

    public String getComment() {
        return comment;
    }

    public UUID getReviewerUserId() {
        return reviewerUserId;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
