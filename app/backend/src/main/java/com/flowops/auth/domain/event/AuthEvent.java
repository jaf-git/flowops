package com.flowops.auth.domain.event;

import com.flowops.auth.domain.enums.AuthAction;
import com.flowops.auth.domain.model.EmailAddress;
import com.flowops.auth.domain.model.SessionMetadata;
import com.flowops.auth.domain.model.UserId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class AuthEvent {
    private final UUID id;
    private final AuthAction action;
    private final UserId actor;
    private final UserId target;
    private final EmailAddress subject;
    private final Instant occurredAt;
    private final SessionMetadata metadata;

    private AuthEvent(
            UUID id,
            AuthAction action,
            UserId actor,
            UserId target,
            EmailAddress subject,
            Instant occurredAt,
            SessionMetadata metadata) {
        this.id = Objects.requireNonNull(id);
        this.action = Objects.requireNonNull(action);
        this.actor = actor;
        this.target = target;
        this.subject = subject;
        this.occurredAt = Objects.requireNonNull(occurredAt);
        this.metadata = Objects.requireNonNull(metadata);
    }

    public static AuthEvent byActor(AuthAction action, UserId actor, Instant occurredAt, SessionMetadata metadata) {
        return new AuthEvent(UUID.randomUUID(), action, actor, actor, null, occurredAt, metadata);
    }

    public static AuthEvent byActorUpon(
            AuthAction action, UserId actor, UserId target, Instant occurredAt, SessionMetadata metadata) {
        return new AuthEvent(UUID.randomUUID(), action, actor, target, null, occurredAt, metadata);
    }

    public static AuthEvent forSubject(
            AuthAction action, EmailAddress subject, Instant occurredAt, SessionMetadata metadata) {
        return new AuthEvent(UUID.randomUUID(), action, null, null, subject, occurredAt, metadata);
    }

    public UUID id() {
        return id;
    }

    public AuthAction action() {
        return action;
    }

    public Optional<UserId> actor() {
        return Optional.ofNullable(actor);
    }

    public Optional<UserId> target() {
        return Optional.ofNullable(target);
    }

    public Optional<EmailAddress> subject() {
        return Optional.ofNullable(subject);
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    public SessionMetadata metadata() {
        return metadata;
    }
}
