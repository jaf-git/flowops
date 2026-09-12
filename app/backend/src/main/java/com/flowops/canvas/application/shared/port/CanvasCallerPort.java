package com.flowops.canvas.application.shared.port;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface CanvasCallerPort {
    Optional<UUID> currentCaller();

    Set<String> callerPermissions();
}
