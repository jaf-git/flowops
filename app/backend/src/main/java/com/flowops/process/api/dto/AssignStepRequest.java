package com.flowops.process.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record AssignStepRequest(
        @NotNull @Schema(description = "The person this step goes to") UUID assigneeId,
        @Schema(
                        description = "When it is due. Optional: the Process Owner may suggest a date, and the"
                                + " assignee sets it after accepting. A template cannot carry one, so demanding it"
                                + " here made somebody guess how long another person's work would take.")
                Instant deadline) {}
