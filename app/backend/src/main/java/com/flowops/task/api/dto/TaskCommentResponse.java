package com.flowops.task.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record TaskCommentResponse(
        UUID id,
        UUID authorId,
        @Schema(description = "Empty when the author has been erased; the screen renders that as Former member.")
                String authorName,
        String body,
        Instant writtenAt) {}
