package com.flowops.workspace.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workspace")
public class WorkspaceJpaEntity {
    @Id
    private UUID id;

    @Column(name = "name")
    private String name;

    @Column(name = "workspace_use")
    private String workspaceUse;

    @Column(name = "singleton", nullable = false)
    private boolean singleton;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WorkspaceJpaEntity() {}

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getWorkspaceUse() {
        return workspaceUse;
    }

    public void setWorkspaceUse(String workspaceUse) {
        this.workspaceUse = workspaceUse;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
