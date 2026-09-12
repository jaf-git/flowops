package com.flowops.task.api.dto;

import com.flowops.task.domain.enums.TaskState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TaskActivityResponse(List<Entry> entries) {
    public record Entry(
            @Schema(allowableValues = {"TRANSITION", "COMMENT"}) String kind,
            Instant occurredAt,
            UUID actorId,
            String actorName,
            TaskState from,
            TaskState to,
            String reason,
            boolean overridden,
            String body) {}
}
