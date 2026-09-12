package com.flowops.auth.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_user")
public class AuthUserJpaEntity {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "account_state", nullable = false)
    private String accountState;

    @Column(name = "role_name", nullable = false)
    private String roleName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "setup_completed", nullable = false)
    private boolean setupCompleted;

    @Column(name = "display_name")
    private String displayName;

    protected AuthUserJpaEntity() {}

    public AuthUserJpaEntity(
            UUID id,
            String email,
            String accountState,
            String roleName,
            Instant createdAt,
            boolean setupCompleted,
            String displayName) {
        this.id = id;
        this.email = email;
        this.accountState = accountState;
        this.roleName = roleName;
        this.createdAt = createdAt;
        this.setupCompleted = setupCompleted;
        this.displayName = displayName;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getAccountState() {
        return accountState;
    }

    public String getRoleName() {
        return roleName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isSetupCompleted() {
        return setupCompleted;
    }

    public String getDisplayName() {
        return displayName;
    }
}
