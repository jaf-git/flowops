package com.flowops.aiinsight.application.port;

import java.util.Optional;
import java.util.UUID;

public interface IdentifyCallerPort {
    Optional<UUID> currentCaller();
}
