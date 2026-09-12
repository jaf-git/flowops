package com.flowops.analyser.application.shared.port;

import java.util.Optional;
import java.util.UUID;

public interface IdentifyCallerPort {
    Optional<UUID> currentCaller();
}
