package com.flowops.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Schema(description = "The authenticated caller's identity, permissions, and landing target.")
public record SessionContextResponse(
        UUID userId,
        String email,
        String accountState,
        Set<String> permissions,
        String landingTarget,
        Instant serverTime) {}
