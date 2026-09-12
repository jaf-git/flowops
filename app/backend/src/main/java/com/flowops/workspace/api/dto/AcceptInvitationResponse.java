package com.flowops.workspace.api.dto;

import java.util.Set;
import java.util.UUID;

public record AcceptInvitationResponse(
        UUID userId, String email, String accountState, Set<String> permissions, String landingTarget) {}
