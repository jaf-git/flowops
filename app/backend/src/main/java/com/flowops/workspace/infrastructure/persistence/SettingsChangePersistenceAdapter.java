package com.flowops.workspace.infrastructure.persistence;

import com.flowops.workspace.application.shared.port.SaveSettingsChangePort;
import com.flowops.workspace.domain.model.WorkspaceId;
import com.flowops.workspace.infrastructure.persistence.entity.WorkspaceSettingsChangeJpaEntity;
import com.flowops.workspace.infrastructure.persistence.repository.WorkspaceSettingsChangeJpaRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SettingsChangePersistenceAdapter implements SaveSettingsChangePort {
    private final WorkspaceSettingsChangeJpaRepository changes;

    public SettingsChangePersistenceAdapter(WorkspaceSettingsChangeJpaRepository changes) {
        this.changes = changes;
    }

    @Override
    public void record(UUID eventId, WorkspaceId workspace, List<FieldChange> moved) {
        changes.saveAll(moved.stream()
                .map(change -> new WorkspaceSettingsChangeJpaEntity(
                        UUID.randomUUID(), eventId, change.field(), change.oldValue(), change.newValue()))
                .toList());
    }
}
