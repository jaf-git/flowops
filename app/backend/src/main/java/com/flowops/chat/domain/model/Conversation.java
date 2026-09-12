package com.flowops.chat.domain.model;

import com.flowops.chat.domain.enums.ConversationKind;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class Conversation {
    private final ConversationId id;
    private final UUID workspaceId;
    private final ConversationKind kind;
    private final Instant createdAt;
    private final PersonId participantLo;
    private final PersonId participantHi;

    private final UUID functionalRoleId;

    private final String name;

    private Conversation(
            ConversationId id,
            UUID workspaceId,
            ConversationKind kind,
            Instant createdAt,
            PersonId participantLo,
            PersonId participantHi,
            UUID functionalRoleId,
            String name) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.kind = kind;
        this.createdAt = createdAt;
        this.participantLo = participantLo;
        this.participantHi = participantHi;
        this.functionalRoleId = functionalRoleId;
        this.name = name;
    }

    public static Conversation directBetween(
            ConversationId id, UUID workspaceId, PersonId one, PersonId other, Instant createdAt) {
        Objects.requireNonNull(one, "a participant is required");
        Objects.requireNonNull(other, "a counterpart is required");
        if (one.equals(other)) {
            throw new IllegalArgumentException("a direct conversation needs two different people");
        }

        boolean oneIsLower = one.compareTo(other) < 0;
        return new Conversation(
                id,
                workspaceId,
                ConversationKind.DIRECT,
                createdAt,
                oneIsLower ? one : other,
                oneIsLower ? other : one,
                null,
                null);
    }

    public static Conversation groupNamed(ConversationId id, UUID workspaceId, String name, Instant createdAt) {
        return new Conversation(
                id, workspaceId, ConversationKind.GROUP, createdAt, null, null, null, requireName(name));
    }

    public static Conversation channelForRole(
            ConversationId id, UUID workspaceId, UUID functionalRoleId, String name, Instant createdAt) {
        Objects.requireNonNull(functionalRoleId, "a channel belongs to a functional role");
        return new Conversation(
                id, workspaceId, ConversationKind.CHANNEL, createdAt, null, null, functionalRoleId, requireName(name));
    }

    public static Conversation announcementsFor(ConversationId id, UUID workspaceId, Instant createdAt) {
        return new Conversation(id, workspaceId, ConversationKind.ANNOUNCEMENT, createdAt, null, null, null, null);
    }

    public static Conversation rehydrated(
            ConversationId id,
            UUID workspaceId,
            ConversationKind kind,
            Instant createdAt,
            PersonId participantLo,
            PersonId participantHi,
            UUID functionalRoleId,
            String name) {
        return new Conversation(id, workspaceId, kind, createdAt, participantLo, participantHi, functionalRoleId, name);
    }

    public Conversation renamedTo(String newName) {
        return new Conversation(
                id, workspaceId, kind, createdAt, participantLo, participantHi, functionalRoleId, requireName(newName));
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("a room somebody has to find again needs a name");
        }
        return name.trim();
    }

    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    public Optional<PersonId> counterpartOf(PersonId caller) {
        if (kind != ConversationKind.DIRECT) {
            return Optional.empty();
        }
        return Optional.of(caller.equals(participantLo) ? participantHi : participantLo);
    }

    public boolean hasParticipant(PersonId person) {
        if (kind != ConversationKind.DIRECT) {
            return false;
        }
        return person.equals(participantLo) || person.equals(participantHi);
    }

    public ConversationId id() {
        return id;
    }

    public UUID workspaceId() {
        return workspaceId;
    }

    public ConversationKind kind() {
        return kind;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Optional<PersonId> participantLo() {
        return Optional.ofNullable(participantLo);
    }

    public Optional<PersonId> participantHi() {
        return Optional.ofNullable(participantHi);
    }

    public Optional<UUID> functionalRoleId() {
        return Optional.ofNullable(functionalRoleId);
    }
}
