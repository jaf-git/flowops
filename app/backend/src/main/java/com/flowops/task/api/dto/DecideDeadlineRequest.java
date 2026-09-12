package com.flowops.task.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DecideDeadlineRequest(
        @Schema(description = "True to agree the proposed date, false to refuse it.") @NotNull Boolean accept,
        @Schema(description = "Required when refusing. The assignee raised a real constraint and is owed an answer.")
                @Size(max = 2000)
                String reason) {}
