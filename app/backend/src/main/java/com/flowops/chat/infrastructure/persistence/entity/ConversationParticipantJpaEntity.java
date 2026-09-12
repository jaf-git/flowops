package com.flowops.chat.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "conversation_participant")
public class ConversationParticipantJpaEntity {
    @EmbeddedId
    private Key key;

    @Column(name = "last_read_message_id")
    private UUID lastReadMessageId;

    protected ConversationParticipantJpaEntity() {}

    public ConversationParticipantJpaEntity(UUID conversationId, UUID personId, UUID lastReadMessageId) {
        this.key = new Key(conversationId, personId);
        this.lastReadMessageId = lastReadMessageId;
    }

    public UUID getConversationId() {
        return key.conversationId;
    }

    public UUID getPersonId() {
        return key.personId;
    }

    public UUID getLastReadMessageId() {
        return lastReadMessageId;
    }

    public void setLastReadMessageId(UUID lastReadMessageId) {
        this.lastReadMessageId = lastReadMessageId;
    }

    @Embeddable
    public static class Key implements Serializable {
        @Column(name = "conversation_id", nullable = false)
        private UUID conversationId;

        @Column(name = "person_id", nullable = false)
        private UUID personId;

        protected Key() {}

        Key(UUID conversationId, UUID personId) {
            this.conversationId = conversationId;
            this.personId = personId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return Objects.equals(conversationId, key.conversationId) && Objects.equals(personId, key.personId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(conversationId, personId);
        }
    }
}
