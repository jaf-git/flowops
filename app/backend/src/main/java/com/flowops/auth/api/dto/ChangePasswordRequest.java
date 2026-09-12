package com.flowops.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        @NotBlank
                @Schema(
                        description =
                                "The new password. Must meet the password policy and differ from the current one.",
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        format = "password")
                String newPassword) {}
