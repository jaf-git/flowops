package com.flowops.workspace.api.dto;

import com.flowops.workspace.domain.enums.WorkspaceUse;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SetupWorkspaceRequest(
        @Schema(example = "Maria Ionescu") @NotBlank @Size(max = 120) String ownerName,
        @Schema(example = "Atelier Ionescu") @NotBlank @Size(max = 120) String workspaceName,
        @Schema(example = "WORK") @NotNull WorkspaceUse use,
        @Schema(example = "Europe/Bucharest") @NotBlank @Size(max = 64) String timezone) {}
