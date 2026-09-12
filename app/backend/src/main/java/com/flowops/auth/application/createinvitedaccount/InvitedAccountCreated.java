package com.flowops.auth.application.createinvitedaccount;

import java.util.Set;
import java.util.UUID;

public record InvitedAccountCreated(
        UUID userId, String email, String accountState, Set<String> permissions, String landingTarget) {}
