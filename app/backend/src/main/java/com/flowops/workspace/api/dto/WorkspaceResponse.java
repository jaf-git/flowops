package com.flowops.workspace.api.dto;

import com.flowops.workspace.domain.enums.WorkspaceUse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record WorkspaceResponse(
        UUID id,
        @Schema(example = "Atelier Ionescu") String name,
        WorkspaceUse use,
        @Schema(example = "Europe/Bucharest") String timezone) {}
