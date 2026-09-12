package com.flowops.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record CompletePasswordResetRequest(
        @Schema(description = "The token from the emailed link.", example = "Zm9vYmFy...") @NotBlank String token,
        @Schema(description = "The password to set. Refused with the unmet rule named if it fails policy.") @NotBlank
                String newPassword) {}
