package com.flowops.workspace.api.dto;

import com.flowops.workspace.domain.enums.InvitedRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record InvitePersonRequest(
        @Schema(example = "ionut@atelier.ro") @NotBlank @Email @Size(max = 320) String emailAddress,
        @Schema(example = "EMPLOYEE") @NotNull InvitedRole role,
        @Schema(example = "3f1a6c58-8f7a-4a1e-9f2b-0c5d4e3a2b19") @NotNull UUID managerId) {}
