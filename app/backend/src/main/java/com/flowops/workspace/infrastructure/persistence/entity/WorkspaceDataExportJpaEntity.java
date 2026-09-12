package com.flowops.workspace.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workspace_data_export")
public class WorkspaceDataExportJpaEntity {
    @Id
    private UUID id;

    @Column(name = "subject_user_id", nullable = false)
    private UUID subjectUserId;

    @Column(name = "produced_by_user_id", nullable = false)
    private UUID producedByUserId;

    @Column(name = "produced_at", nullable = false)
    private Instant producedAt;

    protected WorkspaceDataExportJpaEntity() {}

    public WorkspaceDataExportJpaEntity(UUID id, UUID subjectUserId, UUID producedByUserId, Instant producedAt) {
        this.id = id;
        this.subjectUserId = subjectUserId;
        this.producedByUserId = producedByUserId;
        this.producedAt = producedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSubjectUserId() {
        return subjectUserId;
    }

    public UUID getProducedByUserId() {
        return producedByUserId;
    }

    public Instant getProducedAt() {
        return producedAt;
    }
}
