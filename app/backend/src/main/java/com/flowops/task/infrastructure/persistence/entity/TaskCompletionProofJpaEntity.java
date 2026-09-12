package com.flowops.task.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_completion_proof")
public class TaskCompletionProofJpaEntity {
    @Id
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(nullable = false)
    private String note;

    @Column(name = "external_link")
    private String externalLink;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    protected TaskCompletionProofJpaEntity() {}

    public TaskCompletionProofJpaEntity(UUID id, UUID taskId, String note, String externalLink, Instant submittedAt) {
        this.id = id;
        this.taskId = taskId;
        this.note = note;
        this.externalLink = externalLink;
        this.submittedAt = submittedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public String getNote() {
        return note;
    }

    public String getExternalLink() {
        return externalLink;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }
}
