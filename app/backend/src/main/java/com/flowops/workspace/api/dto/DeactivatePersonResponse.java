package com.flowops.workspace.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

public record DeactivatePersonResponse(
        @Schema(description = "The membership whose access ended.") UUID membershipId,
        @Schema(description = "False when the person had already been deactivated and nothing was written.")
                boolean changed,
        @Schema(description = "The memberships whose manager changed as a result.") List<UUID> reportsMoved,
        @Schema(description = "The manager those reports now have. Absent when there were none to move.")
                UUID reportsMovedTo) {}
