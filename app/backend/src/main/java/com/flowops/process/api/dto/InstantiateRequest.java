package com.flowops.process.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record InstantiateRequest(
        @NotNull UUID templateId,
        @NotBlank @Size(max = 200) @Schema(example = "Integrare — Andrei Munteanu") String name,
        @NotNull @Schema(description = "The person who steers this run. May be an employee") UUID processOwnerId) {}
