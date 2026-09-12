package com.flowops.process.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record ProcessMetadataRequest(
        @Size(max = 2000) @Schema(example = "A client signs the contract") String triggerNote,
        @Size(max = 2000) @Schema(example = "The client has approved the final delivery") String endCondition,
        @Size(max = 60) @Schema(example = "Account manager") String ownerRole) {}
