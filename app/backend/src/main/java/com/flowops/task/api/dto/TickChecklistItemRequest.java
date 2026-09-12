package com.flowops.task.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record TickChecklistItemRequest(@Schema(description = "Whether the step is done") @NotNull Boolean done) {}
