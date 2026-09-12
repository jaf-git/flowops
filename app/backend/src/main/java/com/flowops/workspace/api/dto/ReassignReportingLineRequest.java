package com.flowops.workspace.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ReassignReportingLineRequest(
        @Schema(example = "3f1a6c58-8f7a-4a1e-9f2b-0c5d4e3a2b19") @NotNull UUID proposedManagerId) {}
