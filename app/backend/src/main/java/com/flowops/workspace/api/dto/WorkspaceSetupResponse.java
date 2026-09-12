package com.flowops.workspace.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record WorkspaceSetupResponse(WorkspaceResponse workspace, @Schema(example = "TRIAGE") String landingTarget) {}
