package com.flowops.workspace.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateFunctionalRoleRequest(
        @Schema(example = "Paralegal") @NotBlank @Size(max = 60) String name,
        @Schema(example = "11111111-0000-4000-8000-000000000001") @NotNull UUID departmentId) {}
