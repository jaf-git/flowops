package com.flowops.chat.application.viewconversation;

import com.flowops.chat.domain.model.WorkSubject;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record ViewConversationResult(List<Row> messages, long cursor, boolean hasMore) {
    public record Row(
            UUID id,
            UUID authorId,
            String authorName,
            Optional<String> body,
            Instant sentAt,
            Optional<Instant> editedAt,
            Optional<Instant> deletedAt,
            Optional<UUID> convertedTaskId,
            Optional<WorkSubject> work,
            long seq) {}
}
