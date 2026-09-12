package com.flowops.workspace.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record InvitationResponse(
        @Schema(example = "0d0a4c9a-2b7d-4a1e-8c3f-1b2a3c4d5e6f") UUID id,
        @Schema(example = "ionut@atelier.ro") String emailAddress,
        @Schema(example = "EMPLOYEE") String role,
        @Schema(example = "3f1a6c58-8f7a-4a1e-9f2b-0c5d4e3a2b19") UUID managerId,
        @Schema(example = "SENT") String state,
        @Schema(example = "2026-08-11T09:00:00Z") Instant expiresAt) {}
