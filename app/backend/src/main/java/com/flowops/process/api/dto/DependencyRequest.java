package com.flowops.process.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record DependencyRequest(
        @NotNull @Schema(description = "The step that waits") UUID dependentStepId,
        @NotNull @Schema(description = "The step it waits for") UUID dependsOnStepId) {}
