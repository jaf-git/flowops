package com.flowops.task.api.dto;

import com.flowops.task.domain.enums.TaskPriority;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record EditTaskRequest(
        @Schema(description = "The date the work is due. A date in the past is refused.") @NotNull Instant deadline,
        @Schema(description = "How urgent the work is.") @NotNull TaskPriority priority,
        @Schema(description = "What is being asked for. Null or blank clears it.") @Size(max = 4000)
                String description) {}
