package com.flowops.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record ReauthenticateResponse(
        @Schema(description = "When the re-authentication window closes.") Instant reauthenticatedUntil) {}
