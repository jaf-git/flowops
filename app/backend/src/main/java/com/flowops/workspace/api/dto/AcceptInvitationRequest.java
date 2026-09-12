package com.flowops.workspace.api.dto;

import jakarta.validation.constraints.NotBlank;

public record AcceptInvitationRequest(
        String displayName, String password, boolean consentAccepted, @NotBlank String consentVersion) {}
