package com.flowops.task.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectTaskRequest(
        @Schema(description = "Why this is not yours to do. A rejection without one is unactionable for the assigner.")
                @NotBlank
                @Size(max = 2000)
                String reason) {}
