package com.flowops.chat.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "A page of a conversation, newest first; page backwards with `before`.")
public record MessagesResponse(
        List<Row> messages,
        @Schema(description = "The stream cursor, cut inside the same read") long cursor,
        @Schema(description = "Whether history remains above this page") boolean hasMore) {
    public record Row(
            @Schema(description = "SPOKEN or WORK_MARK", allowableValues = "SPOKEN,WORK_MARK") String kind,
            UUID id,
            @Schema(description = "Who said it, or who created the work") UUID authorId,
            String authorName,
            @Schema(description = "Absent on a work mark — nobody typed one") String body,
            Instant sentAt,
            Instant editedAt,
            Instant deletedAt,
            @Schema(description = "The task this message became, for its participants only") UUID convertedTaskId,
            @Schema(description = "Present only on a work mark") Work work,
            long seq) {}

    public record Work(@Schema(description = "TASK or RUN", allowableValues = "TASK,RUN") String kind, UUID id) {}
}
