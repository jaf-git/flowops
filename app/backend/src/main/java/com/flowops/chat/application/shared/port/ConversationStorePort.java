package com.flowops.chat.application.shared.port;

import com.flowops.chat.domain.model.Conversation;
import com.flowops.chat.domain.model.ConversationId;
import com.flowops.chat.domain.model.PersonId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationStorePort {
    List<Participation> participationsOf(PersonId caller);

    Optional<Conversation> findParticipatedBy(ConversationId conversation, PersonId caller);

    Optional<Conversation> findDirectBetween(UUID workspaceId, PersonId one, PersonId other);

    Conversation open(Conversation conversation, List<PersonId> participants);

    void advanceReadMarker(ConversationId conversation, PersonId caller, UUID throughMessageId);

    List<PersonId> participantsOf(ConversationId conversation);

    Optional<Conversation> find(ConversationId conversation);

    Optional<Conversation> findChannelForRole(UUID workspaceId, UUID functionalRoleId);

    Optional<Conversation> findAnnouncements(UUID workspaceId);

    void addParticipant(ConversationId conversation, PersonId person);

    void removeParticipant(ConversationId conversation, PersonId person);

    void rename(ConversationId conversation, String name);

    List<Conversation> openRooms(UUID workspaceId);

    record Participation(
            Conversation conversation,
            Optional<UUID> lastMessageId,
            Optional<String> lastMessagePreview,
            Optional<java.time.Instant> lastMessageAt,
            boolean lastMessageDeleted,
            long unreadCount) {}
}
