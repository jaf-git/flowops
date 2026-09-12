package com.flowops.auth.application.viewsetupstate;

import com.flowops.auth.domain.model.User;

public record SetupState(java.util.UUID userId, boolean ownsSetup, boolean completed, String landingTarget) {
    public static SetupState of(User user) {
        return new SetupState(
                user.id().value(),
                user.role().ownsWorkspaceSetup(),
                user.setupCompleted(),
                user.landingTarget().name());
    }
}
