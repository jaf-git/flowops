package com.flowops.chat.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "The caller's conversations, most recent first, with Announcements pinned.")
public record ConversationsResponse(
        List<Row> conversations,
        @Schema(description = "Open groups the caller has not joined. A name and nothing else.") List<Room> joinable,
        @Schema(description = "The stream cursor") long cursor) {
    public record Row(
            UUID id,
            @Schema(description = "DIRECT, GROUP, CHANNEL or ANNOUNCEMENT") String kind,
            @Schema(description = "The other person; absent for a room or Announcements") UUID counterpartId,
            String counterpartName,
            @Schema(description = "False for a deactivated colleague — the thread renders read-only")
                    boolean counterpartActive,
            @Schema(
                            description = "The room's own name — a group's chosen one, a channel's role. "
                                    + "Absent for a direct and for Announcements.")
                    String name,
            String lastMessagePreview,
            @Schema(description = "The last message was withdrawn; the row keeps its place in the order")
                    boolean lastMessageDeleted,
            Instant lastMessageAt,
            @Schema(description = "The caller's own. No route returns anybody else's") long unreadCount) {}

    public record Room(UUID id, String name) {}
}
