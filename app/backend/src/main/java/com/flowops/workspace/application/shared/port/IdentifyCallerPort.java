package com.flowops.workspace.application.shared.port;

import com.flowops.workspace.domain.model.PersonId;
import java.util.Optional;

public interface IdentifyCallerPort {
    Optional<Caller> currentCaller();

    record Caller(PersonId id, boolean ownsWorkspaceSetup, boolean setupCompleted, String landingTarget) {}
}
