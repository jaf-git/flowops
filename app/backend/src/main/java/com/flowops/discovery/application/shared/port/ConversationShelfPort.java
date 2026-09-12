package com.flowops.discovery.application.shared.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ConversationShelfPort {
    List<ShelfItem> shelfOf(UUID conversationId);

    void place(UUID conversationId, String kind, String value, String label, UUID placedBy, Instant at);

    void remove(UUID itemId, Instant at);

    record ShelfItem(
            UUID id, String kind, String value, String label, UUID placedBy, String placedByName, Instant placedAt) {}
}
