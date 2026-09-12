package com.flowops.task.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record ProposeDeadlineRequest(
        @Schema(description = "The date being asked for. Earlier than the current deadline is permitted.") @NotNull
                Instant proposedDeadline,
        @Schema(description = "Why the current date cannot be met. The whole value of the proposal.")
                @NotBlank
                @Size(max = 2000)
                String reason) {}
