package com.flowops.canvas.application.shared.port;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface InstanceScopePort {
    Optional<UUID> instanceOf(UUID task);

    boolean mayView(UUID person, Set<String> permissions, UUID instance);
}
