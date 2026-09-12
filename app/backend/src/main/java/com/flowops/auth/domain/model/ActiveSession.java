package com.flowops.auth.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ActiveSession(
        UUID reference, Instant createdAt, Instant lastActiveAt, SessionMetadata metadata, boolean current) {
    public ActiveSession {
        Objects.requireNonNull(reference, "a session reference is required");
        Objects.requireNonNull(createdAt, "a creation instant is required");
        Objects.requireNonNull(lastActiveAt, "a last-active instant is required");
        Objects.requireNonNull(metadata, "session metadata is required; use unknown");
    }
}
