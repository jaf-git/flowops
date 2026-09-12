package com.flowops.discovery.application.shared.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ConversationWorkPort {
    List<ConversationBracket> workIn(UUID conversationId);

    List<ConversationWait> waitsIn(UUID conversationId);

    record ConversationBracket(
            UUID bracketId,
            String address,
            String workType,
            String state,
            String closeKind,
            String outputValue,
            String outputKind,
            UUID performerId,
            String performerName,
            List<UUID> messageIds,
            Instant openedAt,
            Instant lastActivityAt,
            boolean nudged,
            int openWaits) {
        public boolean isLive() {
            return "OPEN".equals(state) || "WAITING".equals(state);
        }
    }

    record ConversationWait(
            UUID waitId,
            UUID bracketId,
            String kind,
            boolean external,
            String reason,
            Instant expectedBy,
            Instant openedAt,
            String blockingAddress) {}
}
