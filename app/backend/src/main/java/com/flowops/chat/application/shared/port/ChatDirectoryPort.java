package com.flowops.chat.application.shared.port;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatDirectoryPort {
    UUID currentWorkspaceId();

    Optional<Person> describe(UUID userId);

    List<Person> describeAll(Collection<UUID> userIds);

    record Person(UUID userId, String displayName, boolean active) {}
}
