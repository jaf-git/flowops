package com.flowops.task.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BlockTaskRequest(
        @Schema(description = "What the work is waiting on. A manager cannot help with a problem they cannot see.")
                @NotBlank
                @Size(max = 2000)
                String reason) {}
