package com.flowops.auth.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class ResetToken {
    private final UUID id;
    private final UserId userId;
    private final String tokenHash;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private final Instant spentAt;

    private ResetToken(UUID id, UserId userId, String tokenHash, Instant issuedAt, Instant expiresAt, Instant spentAt) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.tokenHash = Objects.requireNonNull(tokenHash);
        this.issuedAt = Objects.requireNonNull(issuedAt);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.spentAt = spentAt;
    }

    public static ResetToken issue(UserId userId, String tokenHash, Instant issuedAt, Duration lifetime) {
        return new ResetToken(UUID.randomUUID(), userId, tokenHash, issuedAt, issuedAt.plus(lifetime), null);
    }

    public static ResetToken rebuild(
            UUID id, UserId userId, String tokenHash, Instant issuedAt, Instant expiresAt, Instant spentAt) {
        return new ResetToken(id, userId, tokenHash, issuedAt, expiresAt, spentAt);
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isSpent() {
        return spentAt != null;
    }

    public boolean isUsable(Instant now) {
        return !isSpent() && !isExpired(now);
    }

    public ResetToken spendAt(Instant when) {
        return new ResetToken(id, userId, tokenHash, issuedAt, expiresAt, Objects.requireNonNull(when));
    }

    public UUID id() {
        return id;
    }

    public UserId userId() {
        return userId;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant spentAt() {
        return spentAt;
    }
}
