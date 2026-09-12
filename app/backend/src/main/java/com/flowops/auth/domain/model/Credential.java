package com.flowops.auth.domain.model;

import com.flowops.auth.domain.service.PasswordHasher;
import java.time.Instant;
import java.util.Objects;

public final class Credential {
    private final UserId userId;
    private final String passwordHash;
    private final String algorithm;
    private final Instant updatedAt;

    private Credential(UserId userId, String passwordHash, String algorithm, Instant updatedAt) {
        this.userId = Objects.requireNonNull(userId);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.algorithm = Objects.requireNonNull(algorithm);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static Credential issue(UserId userId, String rawPassword, PasswordHasher hasher, Instant issuedAt) {
        return new Credential(userId, hasher.hash(rawPassword), hasher.algorithm(), issuedAt);
    }

    public static Credential rebuild(UserId userId, String passwordHash, String algorithm, Instant updatedAt) {
        return new Credential(userId, passwordHash, algorithm, updatedAt);
    }

    public boolean matches(String rawPassword, PasswordHasher hasher) {
        return hasher.matches(rawPassword, passwordHash);
    }

    public Credential replaceWith(String rawPassword, PasswordHasher hasher, Instant changedAt) {
        return issue(userId, rawPassword, hasher, changedAt);
    }

    public UserId userId() {
        return userId;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String algorithm() {
        return algorithm;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
