package com.flowops.chat.infrastructure.persistence.repository;

import com.flowops.chat.infrastructure.persistence.entity.MessageJpaEntity;
import com.flowops.chat.infrastructure.persistence.entity.ThreadEntryKind;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageJpaRepository extends JpaRepository<MessageJpaEntity, UUID> {
    List<MessageJpaEntity> findByConversationIdOrderBySeqDesc(UUID conversationId, Pageable page);

    List<MessageJpaEntity> findByConversationIdAndSeqLessThanOrderBySeqDesc(
            UUID conversationId, long beforeSeq, Pageable page);

    Optional<MessageJpaEntity> findFirstByConversationIdOrderBySeqDesc(UUID conversationId);

    Optional<MessageJpaEntity> findFirstByConversationIdAndKindOrderBySeqDesc(
            UUID conversationId, ThreadEntryKind kind);

    Optional<MessageJpaEntity> findByConvertedTaskId(UUID taskId);

    @Query(
            """
            select count(m) from MessageJpaEntity m
            where m.conversationId = :conversationId
              and m.authorId <> :personId
              and m.seq > coalesce(
                  (select r.seq from MessageJpaEntity r where r.id = :lastReadMessageId), 0)
            """)
    long countUnread(
            @Param("conversationId") UUID conversationId,
            @Param("personId") UUID personId,
            @Param("lastReadMessageId") UUID lastReadMessageId);

    @Query("select coalesce(max(m.seq), 0) from MessageJpaEntity m")
    long currentCursor();
}
