package com.flowops.chat.domain.model;

import com.flowops.chat.domain.exception.MessageAlreadyConvertedException;
import com.flowops.chat.domain.exception.MessageDeletedException;
import com.flowops.chat.domain.exception.MessageEmptyException;
import com.flowops.chat.domain.exception.MessageTooLongException;
import com.flowops.chat.domain.exception.NotTheAuthorException;
import com.flowops.chat.domain.exception.NothingChangedException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class Message implements ThreadEntry {
    public static final int MAX_BODY_LENGTH = 4000;

    private final MessageId id;
    private final ConversationId conversation;
    private final PersonId author;
    private final Instant sentAt;

    private String body;
    private Instant editedAt;
    private Instant deletedAt;
    private UUID convertedTaskId;

    private Message(
            MessageId id,
            ConversationId conversation,
            PersonId author,
            String body,
            Instant sentAt,
            Instant editedAt,
            Instant deletedAt,
            UUID convertedTaskId) {
        this.id = id;
        this.conversation = conversation;
        this.author = author;
        this.body = body;
        this.sentAt = sentAt;
        this.editedAt = editedAt;
        this.deletedAt = deletedAt;
        this.convertedTaskId = convertedTaskId;
    }

    public static Message sent(
            MessageId id, ConversationId conversation, PersonId author, String body, Instant sentAt) {
        String checked = validBody(body);
        return new Message(
                id,
                Objects.requireNonNull(conversation, "a conversation is required"),
                Objects.requireNonNull(author, "an author is required"),
                checked,
                Objects.requireNonNull(sentAt, "a time is required"),
                null,
                null,
                null);
    }

    public static Message rehydrated(
            MessageId id,
            ConversationId conversation,
            PersonId author,
            String body,
            Instant sentAt,
            Instant editedAt,
            Instant deletedAt,
            UUID convertedTaskId) {
        return new Message(id, conversation, author, body, sentAt, editedAt, deletedAt, convertedTaskId);
    }

    public void editedBy(PersonId actor, String newBody, Instant at) {
        requireAuthor(actor);
        requireNotDeleted();
        String checked = validBody(newBody);
        if (checked.equals(body)) {
            throw new NothingChangedException();
        }
        this.body = checked;
        this.editedAt = at;
    }

    public void deletedBy(PersonId actor, Instant at) {
        requireAuthor(actor);
        requireNotDeleted();
        this.deletedAt = at;
    }

    public void becameTask(UUID taskId) {
        requireConvertible();
        this.convertedTaskId = Objects.requireNonNull(taskId, "a task identifier is required");
    }

    public void requireConvertible() {
        requireNotDeleted();
        if (convertedTaskId != null) {
            throw new MessageAlreadyConvertedException(convertedTaskId);
        }
    }

    private void requireAuthor(PersonId actor) {
        if (!author.equals(actor)) {
            throw new NotTheAuthorException();
        }
    }

    private void requireNotDeleted() {
        if (deletedAt != null) {
            throw new MessageDeletedException();
        }
    }

    private static String validBody(String body) {
        if (body == null || body.isBlank()) {
            throw new MessageEmptyException();
        }
        String trimmed = body.strip();
        if (trimmed.length() > MAX_BODY_LENGTH) {
            throw new MessageTooLongException(MAX_BODY_LENGTH, trimmed.length());
        }
        return trimmed;
    }

    public MessageId id() {
        return id;
    }

    public ConversationId conversation() {
        return conversation;
    }

    public PersonId author() {
        return author;
    }

    public String body() {
        return body;
    }

    public Instant sentAt() {
        return sentAt;
    }

    public Optional<Instant> editedAt() {
        return Optional.ofNullable(editedAt);
    }

    public Optional<Instant> deletedAt() {
        return Optional.ofNullable(deletedAt);
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public Optional<UUID> convertedTaskId() {
        return Optional.ofNullable(convertedTaskId);
    }
}
