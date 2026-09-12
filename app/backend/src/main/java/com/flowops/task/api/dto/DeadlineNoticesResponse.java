package com.flowops.task.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DeadlineNoticesResponse(List<DeadlineNotice> notices) {
    public record DeadlineNotice(
            @Schema(description = "The task the date is on") UUID taskId,
            String taskTitle,
            UUID assigneeId,
            String assigneeName,
            @Schema(description = "The date they chose") Instant deadline,
            @Schema(description = "When they chose it") Instant setAt) {}
}
