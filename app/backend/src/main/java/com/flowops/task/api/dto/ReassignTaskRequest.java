package com.flowops.task.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ReassignTaskRequest(
        @NotNull
                @Schema(
                        description = "The person the work is moving to. Must be active and within your"
                                + " reporting scope.")
                UUID newAssigneeId,
        @NotBlank
                @Size(max = 2000)
                @Schema(
                        description = "Why the work is moving. The person who had it will read this.",
                        example = "Andrei este în concediu până luni.")
                String reason) {}
