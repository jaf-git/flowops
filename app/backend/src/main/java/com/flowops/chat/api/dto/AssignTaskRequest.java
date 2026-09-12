package com.flowops.chat.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "A task for the conversation's counterpart. The assignee is not a parameter.")
public record AssignTaskRequest(
        String title,
        String description,
        @Schema(description = "Refused by TASK if absent or in the past") Instant deadline,
        String priority) {}
