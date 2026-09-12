package com.flowops.task.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record ChecklistItemResponse(
        UUID id,
        int position,
        String text,
        boolean done,
        @Schema(description = "When it was ticked; absent while it is not done") Instant doneAt,
        UUID authoredById) {}
