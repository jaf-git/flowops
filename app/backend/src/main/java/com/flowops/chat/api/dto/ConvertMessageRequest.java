package com.flowops.chat.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record ConvertMessageRequest(
        @NotNull String title,
        String description,
        @Schema(description = "Who does the work. TASK decides whether the caller may name them") @NotNull
                UUID assigneeId,
        @Schema(description = "Optional. The assignee sets it after accepting where absent") Instant deadline,
        @NotNull String priority,
        @Schema(description = "Optional. The run to place this work in; absent means no process") UUID instanceId) {}
