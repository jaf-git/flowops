package com.flowops.task.infrastructure.persistence.entity;

import com.flowops.task.domain.enums.ProposalDecision;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_deadline_proposal")
public class TaskDeadlineProposalJpaEntity {
    @Id
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "proposed_deadline", nullable = false)
    private Instant proposedDeadline;

    @Column(nullable = false)
    private String reason;

    @Column(name = "proposer_user_id", nullable = false)
    private UUID proposerUserId;

    @Column(name = "proposed_at", nullable = false)
    private Instant proposedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision")
    private ProposalDecision decision;

    @Column(name = "decision_reason")
    private String decisionReason;

    @Column(name = "decided_by_user_id")
    private UUID decidedByUserId;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected TaskDeadlineProposalJpaEntity() {}

    public TaskDeadlineProposalJpaEntity(
            UUID id,
            UUID taskId,
            Instant proposedDeadline,
            String reason,
            UUID proposerUserId,
            Instant proposedAt,
            ProposalDecision decision,
            String decisionReason,
            UUID decidedByUserId,
            Instant decidedAt) {
        this.id = id;
        this.taskId = taskId;
        this.proposedDeadline = proposedDeadline;
        this.reason = reason;
        this.proposerUserId = proposerUserId;
        this.proposedAt = proposedAt;
        this.decision = decision;
        this.decisionReason = decisionReason;
        this.decidedByUserId = decidedByUserId;
        this.decidedAt = decidedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public Instant getProposedDeadline() {
        return proposedDeadline;
    }

    public String getReason() {
        return reason;
    }

    public UUID getProposerUserId() {
        return proposerUserId;
    }

    public Instant getProposedAt() {
        return proposedAt;
    }

    public ProposalDecision getDecision() {
        return decision;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public UUID getDecidedByUserId() {
        return decidedByUserId;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
