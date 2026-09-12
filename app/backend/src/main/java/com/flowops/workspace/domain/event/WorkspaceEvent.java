package com.flowops.workspace.domain.event;

import com.flowops.workspace.domain.enums.WorkspaceAction;
import com.flowops.workspace.domain.model.PersonId;
import com.flowops.workspace.domain.model.WorkspaceId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record WorkspaceEvent(
        UUID id,
        WorkspaceAction action,
        PersonId actor,
        PersonId subject,
        PersonId formerManager,
        PersonId newManager,
        WorkspaceId workspaceId,
        Instant occurredAt) {
    public WorkspaceEvent {
        Objects.requireNonNull(id);
        Objects.requireNonNull(action);
        Objects.requireNonNull(workspaceId);
        Objects.requireNonNull(occurredAt);

        if (action == WorkspaceAction.REPORTING_LINE_CHANGED
                && (subject == null || formerManager == null || newManager == null)) {
            throw new IllegalArgumentException(
                    "a reporting-line change names the person moved, their former manager and their new one");
        }
    }

    public static WorkspaceEvent byActor(
            WorkspaceAction action, PersonId actor, WorkspaceId workspaceId, Instant occurredAt) {
        return new WorkspaceEvent(UUID.randomUUID(), action, actor, null, null, null, workspaceId, occurredAt);
    }

    public static WorkspaceEvent reportingLineChanged(
            PersonId actor,
            PersonId subject,
            PersonId formerManager,
            PersonId newManager,
            WorkspaceId workspaceId,
            Instant occurredAt) {
        return new WorkspaceEvent(
                UUID.randomUUID(),
                WorkspaceAction.REPORTING_LINE_CHANGED,
                actor,
                subject,
                formerManager,
                newManager,
                workspaceId,
                occurredAt);
    }

    public static WorkspaceEvent profileChanged(PersonId person, WorkspaceId workspaceId, Instant occurredAt) {
        return new WorkspaceEvent(
                UUID.randomUUID(),
                WorkspaceAction.PROFILE_CHANGED,
                person,
                Objects.requireNonNull(person, "a profile change names the person whose profile changed"),
                null,
                null,
                workspaceId,
                occurredAt);
    }

    public static WorkspaceEvent personErased(
            PersonId actor, PersonId subject, WorkspaceId workspaceId, Instant occurredAt) {
        return new WorkspaceEvent(
                UUID.randomUUID(),
                WorkspaceAction.PERSON_ERASED,
                actor,
                Objects.requireNonNull(subject, "an erasure names the person erased"),
                null,
                null,
                workspaceId,
                occurredAt);
    }

    public static WorkspaceEvent personDeactivated(
            PersonId actor, PersonId subject, PersonId reportsMovedTo, WorkspaceId workspaceId, Instant occurredAt) {
        return new WorkspaceEvent(
                UUID.randomUUID(),
                WorkspaceAction.PERSON_DEACTIVATED,
                actor,
                subject,
                null,
                reportsMovedTo,
                workspaceId,
                occurredAt);
    }
}
