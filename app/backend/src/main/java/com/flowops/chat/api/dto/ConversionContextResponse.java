package com.flowops.chat.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "The pre-fill for turning a message into a task.")
public record ConversionContextResponse(
        UUID suggestedAssigneeId,
        String suggestedAssigneeName,
        boolean suggestedAssigneeActive,
        @Schema(description = "The message's first line, cut to fit a title") String title,
        @Schema(description = "The message text in full — copied, never referenced") String description,
        @Schema(
                        description = "Running runs this caller may add a task to, the assignee's own first. "
                                + "Empty means the picker does not render at all")
                List<Run> instances) {
    public record Run(UUID id, String name) {}
}
