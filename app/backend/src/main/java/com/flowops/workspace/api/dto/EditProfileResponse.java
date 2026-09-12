package com.flowops.workspace.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record EditProfileResponse(
        @Schema(description = "The name now in force.") String displayName,
        @Schema(description = "False when it was already their name and nothing was written.") boolean changed) {}
