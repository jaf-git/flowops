package com.flowops.process.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public record ReorderTasksRequest(
        @Schema(description = "Every step of this run, in the order they should read.") @NotEmpty List<UUID> stepIds) {}
