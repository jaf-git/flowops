package com.flowops.task.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ApproveTaskRequest(
        @Schema(description = "One to five. It describes this piece of work and is never aggregated per person.")
                @NotNull
                @Min(1)
                @Max(5)
                Integer score,
        @Schema(description = "Optional. What the reviewer wanted to say about the deliverable.") @Size(max = 4000)
                String comment) {}
