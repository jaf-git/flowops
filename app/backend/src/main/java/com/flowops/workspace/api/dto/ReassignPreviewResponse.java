package com.flowops.workspace.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

public record ReassignPreviewResponse(
        @Schema(example = "Ioana Radu") String personName,
        @Schema(example = "Maria Ionescu", nullable = true) String formerManagerName,
        @Schema(example = "Ionuț Petrescu") String newManagerName,
        List<MovingPersonResponse> movingWithThem,
        @Schema(example = "false") boolean alreadyTheirManager) {
    public record MovingPersonResponse(
            @Schema(example = "3f1a6c58-8f7a-4a1e-9f2b-0c5d4e3a2b19") UUID membershipId,
            @Schema(example = "Andrei Munteanu") String displayName) {}
}
