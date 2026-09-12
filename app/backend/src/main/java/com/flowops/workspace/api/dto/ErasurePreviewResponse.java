package com.flowops.workspace.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ErasurePreviewResponse(
        @Schema(description = "The membership being previewed.") UUID membershipId,
        @Schema(description = "The name the caller must type back. Absent for an account that never named anybody.")
                String displayName,
        @Schema(description = "When their access ended. Absent while it has not.", nullable = true)
                Instant deactivatedAt,
        @Schema(description = "Whether erasure would be accepted, before re-authentication and the typed name.")
                boolean eligible,
        @Schema(description = "SUBJECT_ACTIVE or ONLY_OWNER when it would not. Absent when eligible.", nullable = true)
                String refusal,
        @Schema(description = "Keys of what erasure destroys.") List<String> destroys,
        @Schema(description = "Keys of what survives it.") List<String> survives,
        @Schema(description = "True when there is nothing left to destroy.") boolean alreadyErased) {}
