package com.flowops.process.application.streamvisibility;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface InstanceVisibilityUseCase {
    Optional<UUID> instanceOf(UUID task);

    boolean mayView(UUID person, Set<String> permissions, UUID instance);
}
