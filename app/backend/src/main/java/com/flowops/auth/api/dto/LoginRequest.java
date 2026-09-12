package com.flowops.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Schema(description = "The account's address.", example = "founder@example.test") @NotBlank String email,
        @Schema(description = "The account's password.") @NotBlank String password) {}
