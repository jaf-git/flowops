package com.flowops.workspace.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EditProfileRequest(
        @Schema(description = "The name colleagues will see.", example = "Ioana Radu") @NotBlank @Size(max = 120)
                String displayName) {}
