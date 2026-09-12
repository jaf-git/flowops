package com.flowops.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CompleteSignupRequest(
        @Schema(description = "The address the passcode was issued to.", example = "founder@example.test")
                @NotBlank
                @Email
                String email,
        @Schema(description = "The one-time code from the signup email.", example = "000000") @NotBlank String passcode,
        @Schema(description = "The chosen password. At least 12 characters; no composition rules.") @NotBlank
                String password) {}
