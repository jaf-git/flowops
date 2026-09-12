package com.flowops.task.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record SetDeadlineRequest(
        @Schema(description = "When this will be done. The assignee's own call, until work begins.") @NotNull
                Instant deadline) {}
