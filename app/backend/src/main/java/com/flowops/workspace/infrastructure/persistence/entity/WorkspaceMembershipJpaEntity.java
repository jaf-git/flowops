package com.flowops.workspace.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workspace_membership")
public class WorkspaceMembershipJpaEntity {
    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "manager_id")
    private UUID managerId;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

    @Column(name = "erased_at")
    private Instant erasedAt;

    @Column(name = "functional_role_id")
    private UUID functionalRoleId;

    protected WorkspaceMembershipJpaEntity() {}

    public WorkspaceMembershipJpaEntity(
            UUID id, UUID workspaceId, UUID userId, String status, UUID managerId, Instant joinedAt) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.status = status;
        this.managerId = managerId;
        this.joinedAt = joinedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getStatus() {
        return status;
    }

    public UUID getManagerId() {
        return managerId;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public Instant getDeactivatedAt() {
        return deactivatedAt;
    }

    public Instant getErasedAt() {
        return erasedAt;
    }

    public UUID getFunctionalRoleId() {
        return functionalRoleId;
    }

    public void setFunctionalRoleId(UUID functionalRoleId) {
        this.functionalRoleId = functionalRoleId;
    }
}
