package com.flowops.discovery.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NodeTrailResponse(
        UUID nodeId,
        @Schema(description = "False where the node predates the trail; its earlier moves are gone, not absent")
                boolean recordedFromTheStart,
        List<Move> moves) {
    public record Move(String from, String to, UUID actorId, String reason, Instant occurredAt) {}
}
