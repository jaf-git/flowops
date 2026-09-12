package com.flowops.automation.application.shared.port;

import java.util.Optional;
import java.util.UUID;

public interface ReportingLinePort {
    Optional<UUID> reachable(UUID person);

    Optional<UUID> managerAbove(UUID person);
}
