package com.flowops.workspace.application.acceptinvite;

import java.util.Set;
import java.util.UUID;

public record AcceptInvitationResult(
        UUID userId, String email, String accountState, Set<String> permissions, String landingTarget) {}
