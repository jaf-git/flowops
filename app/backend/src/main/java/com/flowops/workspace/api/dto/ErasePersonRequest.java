package com.flowops.workspace.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record ErasePersonRequest(
        @Schema(description = "The person's name, typed by the caller.", example = "Ionuț Petrescu") @NotBlank
                String typedName) {}
