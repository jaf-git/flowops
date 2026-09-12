package com.flowops.workspace.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record InvitationPreviewResponse(
        @Schema(example = "Atelier București") String workspaceName,
        @Schema(example = "EMPLOYEE") String role,
        ManagerResponse manager,
        InviterResponse inviter,
        ConsentResponse consent) {
    public record ManagerResponse(
            @Schema(example = "Ioana Radu") String displayName, @Schema(example = "false") boolean reassigned) {}

    public record InviterResponse(@Schema(example = "Maria Enache") String displayName) {}

    public record ConsentResponse(@Schema(example = "a3f2c1e09b4d") String version, String text) {}
}
