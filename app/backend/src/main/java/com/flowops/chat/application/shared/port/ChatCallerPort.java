package com.flowops.chat.application.shared.port;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ChatCallerPort {
    Optional<UUID> currentCaller();

    Set<String> callerPermissions();
}
