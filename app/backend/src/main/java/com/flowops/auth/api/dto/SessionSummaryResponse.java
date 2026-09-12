package com.flowops.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "An active session, with enough context to recognise it and nothing that could be replayed.")
public record SessionSummaryResponse(
        @Schema(description = "Opaque handle for this session. Not the session identifier, and not usable as one.")
                UUID reference,
        @Schema(description = "True for the session this request arrived on.") boolean current,
        Instant createdAt,
        Instant lastActiveAt,
        @Schema(description = "As seen by the server; unknown when it could not be determined.") String ipAddress,
        String deviceSummary,
        @Schema(description = "City-level at best, and unknown on a local network.") String coarseLocation) {}
