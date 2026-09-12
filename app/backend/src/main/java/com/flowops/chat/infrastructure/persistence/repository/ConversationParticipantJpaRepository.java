package com.flowops.chat.infrastructure.persistence.repository;

import com.flowops.chat.infrastructure.persistence.entity.ConversationParticipantJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationParticipantJpaRepository
        extends JpaRepository<ConversationParticipantJpaEntity, ConversationParticipantJpaEntity.Key> {
    List<ConversationParticipantJpaEntity> findByKeyPersonId(UUID personId);

    Optional<ConversationParticipantJpaEntity> findByKeyConversationIdAndKeyPersonId(
            UUID conversationId, UUID personId);

    List<ConversationParticipantJpaEntity> findByKeyConversationId(UUID conversationId);
}
