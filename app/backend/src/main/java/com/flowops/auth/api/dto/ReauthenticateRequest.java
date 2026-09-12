package com.flowops.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record ReauthenticateRequest(
        @NotBlank
                @Schema(
                        description = "The caller's current password.",
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        format = "password")
                String password) {}
