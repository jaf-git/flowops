package com.flowops.process.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "process_category")
public class ProcessCategoryJpaEntity {
    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProcessCategoryJpaEntity() {}

    public ProcessCategoryJpaEntity(UUID id, UUID workspaceId, String name, Instant createdAt) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.name = name;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
