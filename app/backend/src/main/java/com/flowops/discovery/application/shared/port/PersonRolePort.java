package com.flowops.discovery.application.shared.port;

import java.util.Optional;
import java.util.UUID;

public interface PersonRolePort {
    Optional<UUID> roleOf(UUID personId);

    boolean isActiveMember(UUID personId);

    Optional<UUID> managerOf(UUID personId);

    boolean ownsTheWorkspace(UUID personId);
}
