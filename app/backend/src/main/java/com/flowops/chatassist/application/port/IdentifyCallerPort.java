package com.flowops.chatassist.application.port;

import java.util.Optional;
import java.util.UUID;

public interface IdentifyCallerPort {
    Optional<UUID> currentCaller();
}
