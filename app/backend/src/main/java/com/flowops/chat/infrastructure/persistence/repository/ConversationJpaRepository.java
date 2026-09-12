package com.flowops.chat.infrastructure.persistence.repository;

import com.flowops.chat.infrastructure.persistence.entity.ConversationJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationJpaRepository extends JpaRepository<ConversationJpaEntity, UUID> {
    Optional<ConversationJpaEntity> findByWorkspaceIdAndParticipantLoAndParticipantHi(
            UUID workspaceId, UUID participantLo, UUID participantHi);

    Optional<ConversationJpaEntity> findByWorkspaceIdAndKind(UUID workspaceId, String kind);

    Optional<ConversationJpaEntity> findByWorkspaceIdAndFunctionalRoleId(UUID workspaceId, UUID functionalRoleId);

    List<ConversationJpaEntity> findByWorkspaceIdAndKindOrderByNameAsc(UUID workspaceId, String kind);
}
