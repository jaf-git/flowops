package com.flowops.tasklib.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "A job the workspace keeps typing, with the drafts it produced")
public record DraftCandidateResponse(
        @Schema(description = "The most recent wording, which is the one an owner will recognise") String title,
        @Schema(description = "Every distinct wording seen for this job") List<String> variants,
        @Schema(description = "How many drafts describe it") int drafts,
        @Schema(description = "When somebody first typed it") Instant firstSeen,
        @Schema(description = "The most recent time somebody typed it") Instant lastSeen,
        @Schema(description = "The drafts themselves, so a decision can act on the group") List<UUID> templateIds) {}
