package com.flowops.nodepipeline.application.port;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface PipelineAudiencePort {
    UUID workspace();

    Optional<UUID> workspaceOwner();

    Map<String, UUID> openedBy(Collection<String> jobIds);

    Map<String, UUID> markedBy(Collection<String> nodeIds);

    Set<UUID> stillHere(Collection<UUID> people);

    Set<String> alreadyNudged(Collection<String> nodeIds);
}
