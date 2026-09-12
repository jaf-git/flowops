package com.flowops.auth.domain.model;

import com.flowops.auth.domain.service.PasswordHasher;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class SignupPasscode {
    private final UUID id;
    private final EmailAddress email;
    private final String codeHash;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private final boolean used;
    private final int failureCount;

    private SignupPasscode(
            UUID id,
            EmailAddress email,
            String codeHash,
            Instant issuedAt,
            Instant expiresAt,
            boolean used,
            int failureCount) {
        this.id = Objects.requireNonNull(id);
        this.email = Objects.requireNonNull(email);
        this.codeHash = Objects.requireNonNull(codeHash);
        this.issuedAt = Objects.requireNonNull(issuedAt);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.used = used;
        this.failureCount = failureCount;
    }

    public static SignupPasscode issue(
            EmailAddress email, String rawCode, PasswordHasher hasher, Instant issuedAt, Duration lifetime) {
        return new SignupPasscode(
                UUID.randomUUID(), email, hasher.hash(rawCode), issuedAt, issuedAt.plus(lifetime), false, 0);
    }

    public static SignupPasscode rebuild(
            UUID id,
            EmailAddress email,
            String codeHash,
            Instant issuedAt,
            Instant expiresAt,
            boolean used,
            int failureCount) {
        return new SignupPasscode(id, email, codeHash, issuedAt, expiresAt, used, failureCount);
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isLocked(int failureCeiling) {
        return failureCount >= failureCeiling;
    }

    public boolean isUsable(Instant now, int failureCeiling) {
        return !used && !isExpired(now) && !isLocked(failureCeiling);
    }

    public boolean matches(String rawCode, PasswordHasher hasher) {
        return hasher.matches(rawCode, codeHash);
    }

    public SignupPasscode markUsed() {
        return new SignupPasscode(id, email, codeHash, issuedAt, expiresAt, true, failureCount);
    }

    public SignupPasscode recordFailure() {
        return new SignupPasscode(id, email, codeHash, issuedAt, expiresAt, used, failureCount + 1);
    }

    public UUID id() {
        return id;
    }

    public EmailAddress email() {
        return email;
    }

    public String codeHash() {
        return codeHash;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public boolean used() {
        return used;
    }

    public int failureCount() {
        return failureCount;
    }
}
