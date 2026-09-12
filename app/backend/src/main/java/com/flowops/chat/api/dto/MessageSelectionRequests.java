package com.flowops.chat.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MessageSelectionRequests {
    private MessageSelectionRequests() {}

    @Schema(description = "The ticked messages. Order is ignored; the thread's own sequence is used.")
    public record DraftFromSelection(@NotEmpty @Size(min = 2, max = 50) List<@NotNull UUID> messageIds) {}

    @Schema(description = "Exactly one of `name` (a standalone run) or `templateId` (appended to a template).")
    public record BuildProcess(
            @Size(max = 200) @Schema(description = "Names a new standalone run.") String name,
            @Schema(description = "Appends to this existing template instead. No run is started.") UUID templateId,
            @NotEmpty @Size(min = 2, max = 50) @Valid List<@NotNull Step> steps) {
        public boolean isStandaloneRun() {
            return templateId == null;
        }
    }

    public record Step(
            @NotNull UUID messageId,
            @NotNull @Size(max = 200) String title,
            String description,
            UUID assigneeId,
            Instant deadline) {}
}
