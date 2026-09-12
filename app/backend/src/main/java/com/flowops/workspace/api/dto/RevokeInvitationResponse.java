package com.flowops.workspace.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record RevokeInvitationResponse(
        @Schema(example = "0d0a4c9a-2b7d-4a1e-8c3f-1b2a3c4d5e6f") UUID id,
        @Schema(example = "ionut@atelier.ro") String emailAddress,
        @Schema(example = "REVOKED") String state,
        @Schema(example = "2026-08-04T09:00:00Z", nullable = true) Instant revokedAt) {}
