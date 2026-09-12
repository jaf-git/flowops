package com.flowops.chat.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_event")
public class ChatEventJpaEntity {
    @Id
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected ChatEventJpaEntity() {}

    public ChatEventJpaEntity(
            UUID id, UUID conversationId, UUID messageId, String action, UUID actorUserId, Instant occurredAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.messageId = messageId;
        this.action = action;
        this.actorUserId = actorUserId;
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public String getAction() {
        return action;
    }

    public UUID getMessageId() {
        return messageId;
    }
}
