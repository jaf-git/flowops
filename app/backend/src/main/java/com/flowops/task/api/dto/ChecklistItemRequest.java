package com.flowops.task.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChecklistItemRequest(
        @Schema(example = "Reconcile against the ledger") @NotBlank @Size(max = 500) String text) {}
