package com.flowops.discovery.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

public record NodeEnrichmentResponse(
        UUID nodeId,
        @Schema(description = "What people call this work, or null if nobody has said") String title,
        @Schema(description = "The longer description, or null") String detail,
        @Schema(description = "The steps; null means unanswered, [] means deliberately none") List<String> checklist,
        @Schema(description = "False means every field offered was already answered — a success, not a refusal")
                boolean accepted,
        @Schema(
                        description = "Whether THIS caller may still change an answer that is already there: they wrote"
                                + " it and the node is open (A15). The screen offers an edit box on this"
                                + " rather than re-deriving the rule.")
                boolean correctable) {}
