package com.flowops.auth.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_credential")
public class AuthCredentialJpaEntity {
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String algorithm;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AuthCredentialJpaEntity() {}

    public AuthCredentialJpaEntity(UUID userId, String passwordHash, String algorithm, Instant updatedAt) {
        this.userId = userId;
        this.passwordHash = passwordHash;
        this.algorithm = algorithm;
        this.updatedAt = updatedAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
