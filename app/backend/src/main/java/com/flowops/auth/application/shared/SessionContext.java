package com.flowops.auth.application.shared;

import com.flowops.auth.domain.enums.AccountState;
import com.flowops.auth.domain.enums.LandingTarget;
import com.flowops.auth.domain.model.User;
import java.util.Set;
import java.util.UUID;

public record SessionContext(
        UUID userId, String email, AccountState accountState, Set<String> permissions, LandingTarget landingTarget) {
    public static SessionContext of(User user, Set<String> permissions) {
        return new SessionContext(
                user.id().value(),
                user.email().value(),
                user.accountState(),
                Set.copyOf(permissions),
                user.landingTarget());
    }
}
