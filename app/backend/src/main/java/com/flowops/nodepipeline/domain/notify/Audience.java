package com.flowops.nodepipeline.domain.notify;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class Audience {
    private final UUID workspace;
    private final UUID workspaceOwner;
    private final Map<String, UUID> openedBy;
    private final Map<String, UUID> markedBy;
    private final Set<UUID> ableToAct;

    public Audience(
            UUID workspace,
            UUID workspaceOwner,
            Map<String, UUID> openedBy,
            Map<String, UUID> markedBy,
            Set<UUID> ableToAct) {
        this.workspace = workspace;
        this.workspaceOwner = workspaceOwner;
        this.openedBy = Map.copyOf(new LinkedHashMap<>(openedBy));
        this.markedBy = Map.copyOf(new LinkedHashMap<>(markedBy));
        this.ableToAct = Set.copyOf(new LinkedHashSet<>(ableToAct));
    }

    public UUID workspace() {
        return workspace;
    }

    public Optional<Recipient> theWorkspaceOwner() {
        return address(workspaceOwner, Recipient.Standing.WORKSPACE_OWNER);
    }

    public Optional<Recipient> whoOpened(String jobId) {
        return address(openedBy.get(jobId), Recipient.Standing.JOB_OWNER);
    }

    public Optional<Recipient> whoMarked(String nodeId) {
        return address(markedBy.get(nodeId), Recipient.Standing.MARKER);
    }

    public Set<UUID> everybodyAbleToAct() {
        return ableToAct;
    }

    private Optional<Recipient> address(UUID person, Recipient.Standing standing) {
        if (person == null || !ableToAct.contains(person)) {
            return Optional.empty();
        }
        return Optional.of(new Recipient(person, standing));
    }
}
