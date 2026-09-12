package com.flowops.chat.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "A draft run assembled from ticked messages. Nothing exists until it is submitted.")
public record WorkDraftResponse(
        @Schema(description = "Always PROCESS on this path.", example = "PROCESS") String shape,
        @Schema(description = "The run's name, quoted from the earliest message ticked.") Sourced title,
        @Schema(description = "Who would steer it. The person setting it up, marked a suggestion.") Sourced assigneeId,
        @Schema(description = "Always null on this path: a run carries no deadline of its own, its steps do.")
                Sourced deadline,
        List<Step> steps) {
    public record Sourced(
            String value,
            @Schema(description = "FROM_CONVERSATION or SUGGESTED", allowableValues = "FROM_CONVERSATION,SUGGESTED")
                    String source) {}

    public record Step(UUID quotedFrom, Sourced title, String description, Sourced assigneeId, Sourced deadline) {}

    public static Sourced sourced(Instant value, String source) {
        return new Sourced(value == null ? null : value.toString(), source);
    }
}
