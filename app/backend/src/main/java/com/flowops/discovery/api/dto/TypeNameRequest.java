package com.flowops.discovery.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "NameTypeRequest", description = "The owner's name for a discovered shape of work.")
public record TypeNameRequest(
        @Schema(description = "What this shape of work is called here.", example = "Content production")
                @NotBlank
                @Size(max = 200)
                String name) {}
