package com.flowops.auth.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_signup_passcode")
public class AuthSignupPasscodeJpaEntity {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean used;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    protected AuthSignupPasscodeJpaEntity() {}

    public AuthSignupPasscodeJpaEntity(
            UUID id,
            String email,
            String codeHash,
            Instant issuedAt,
            Instant expiresAt,
            boolean used,
            int failureCount) {
        this.id = id;
        this.email = email;
        this.codeHash = codeHash;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.used = used;
        this.failureCount = failureCount;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isUsed() {
        return used;
    }

    public int getFailureCount() {
        return failureCount;
    }
}
