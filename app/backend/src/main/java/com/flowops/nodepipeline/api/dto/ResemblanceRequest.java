package com.flowops.nodepipeline.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record ResemblanceRequest(
        @NotBlank @Schema(example = "Wrote the October Iulius monthly summary, what worked and what to change.")
                String text) {}
