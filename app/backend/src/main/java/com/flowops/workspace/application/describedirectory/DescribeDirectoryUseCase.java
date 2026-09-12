package com.flowops.workspace.application.describedirectory;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface DescribeDirectoryUseCase {
    UUID currentWorkspaceId();

    Optional<Person> describe(UUID userId);

    List<Person> describeAll(Collection<UUID> userIds);

    boolean isWithinScopeOf(UUID actorUserId, UUID subjectUserId);

    Set<UUID> subtreeOf(UUID actorUserId);

    Set<UUID> directReportsOf(UUID managerUserId);

    record Person(UUID userId, String displayName, boolean active) {}
}
