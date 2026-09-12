package com.flowops.chat.application.viewconversations;

import com.flowops.chat.domain.enums.ConversationKind;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record ViewConversationsResult(List<Row> conversations, List<Room> joinable, long cursor) {
    public record Row(
            UUID id,
            ConversationKind kind,
            Optional<UUID> counterpartId,
            Optional<String> counterpartName,
            boolean counterpartActive,
            Optional<String> name,
            Optional<String> lastMessagePreview,
            boolean lastMessageDeleted,
            Optional<Instant> lastMessageAt,
            long unreadCount) {}

    public record Room(UUID id, String name) {}
}
