package com.flowops.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RequestPasswordResetRequest(
        @Schema(description = "The address whose account should be sent a reset link.", example = "maria@atelier.ro")
                @NotBlank
                @Email
                String email) {}
