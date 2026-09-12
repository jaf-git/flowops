package com.flowops.workspace.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workspace_invitation")
public class WorkspaceInvitationJpaEntity {
    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "intended_role", nullable = false)
    private String intendedRole;

    @Column(name = "intended_manager_id", nullable = false)
    private UUID intendedManagerId;

    @Column(name = "inviter_membership_id", nullable = false)
    private UUID inviterMembershipId;

    @Column(name = "state", nullable = false)
    private String state;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "declined_at")
    private Instant declinedAt;

    protected WorkspaceInvitationJpaEntity() {}

    public WorkspaceInvitationJpaEntity(
            UUID id,
            UUID workspaceId,
            String email,
            String intendedRole,
            UUID intendedManagerId,
            UUID inviterMembershipId,
            String state,
            String tokenHash,
            Instant expiresAt,
            Instant createdAt,
            Instant declinedAt) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.email = email;
        this.intendedRole = intendedRole;
        this.intendedManagerId = intendedManagerId;
        this.inviterMembershipId = inviterMembershipId;
        this.state = state;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.declinedAt = declinedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public String getEmail() {
        return email;
    }

    public String getIntendedRole() {
        return intendedRole;
    }

    public UUID getIntendedManagerId() {
        return intendedManagerId;
    }

    public UUID getInviterMembershipId() {
        return inviterMembershipId;
    }

    public String getState() {
        return state;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeclinedAt() {
        return declinedAt;
    }
}
