package com.flowops.chat.domain.model;

import java.time.Instant;
import java.util.Objects;

public final class WorkMark implements ThreadEntry {
    private final MessageId id;
    private final ConversationId conversation;
    private final PersonId actor;
    private final WorkSubject subject;
    private final Instant markedAt;

    private WorkMark(MessageId id, ConversationId conversation, PersonId actor, WorkSubject subject, Instant markedAt) {
        this.id = Objects.requireNonNull(id, "an identifier is required");
        this.conversation = Objects.requireNonNull(conversation, "a conversation is required");
        this.actor = Objects.requireNonNull(actor, "an actor is required");
        this.subject = Objects.requireNonNull(subject, "the work this marks is required");
        this.markedAt = Objects.requireNonNull(markedAt, "a time is required");
    }

    public static WorkMark recorded(
            MessageId id, ConversationId conversation, PersonId actor, WorkSubject subject, Instant markedAt) {
        return new WorkMark(id, conversation, actor, subject, markedAt);
    }

    public static WorkMark rehydrated(
            MessageId id, ConversationId conversation, PersonId actor, WorkSubject subject, Instant markedAt) {
        return new WorkMark(id, conversation, actor, subject, markedAt);
    }

    @Override
    public MessageId id() {
        return id;
    }

    @Override
    public ConversationId conversation() {
        return conversation;
    }

    public PersonId actor() {
        return actor;
    }

    public WorkSubject subject() {
        return subject;
    }

    public Instant markedAt() {
        return markedAt;
    }
}
