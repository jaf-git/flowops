package com.flowops.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RequestPasscodeRequest(
        @Schema(description = "The address to send the signup passcode to.", example = "founder@example.test")
                @NotBlank
                @Email
                String email) {}
