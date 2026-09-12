package com.flowops.workspace.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record ReassignReportingLineResponse(
        @Schema(example = "3f1a6c58-8f7a-4a1e-9f2b-0c5d4e3a2b19") UUID membershipId,
        @Schema(example = "b2c3d4e5-6f70-4812-9a3b-4c5d6e7f8091", nullable = true) UUID formerManagerId,
        @Schema(example = "9c8b6f42-1d55-4a0e-9f3a-0b7c5e2d4a10") UUID newManagerId,
        @Schema(example = "true") boolean changed) {}
