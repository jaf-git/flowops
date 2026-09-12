package com.flowops.workspace.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record ErasePersonResponse(
        @Schema(description = "The membership, which survives so the structure it was part of still makes sense.")
                UUID membershipId,
        @Schema(description = "The stable identifier that everything they authored now reads as.")
                UUID opaqueIdentifier,
        @Schema(description = "False when they had already been erased and nothing was destroyed.") boolean changed) {}
