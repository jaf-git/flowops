package com.flowops.workspace.infrastructure.persistence;

import com.flowops.workspace.application.shared.port.AppendWorkspaceEventPort;
import com.flowops.workspace.application.shared.port.LoadWorkspaceEventsPort;
import com.flowops.workspace.application.shared.port.LoadWorkspacePort;
import com.flowops.workspace.application.shared.port.LoadWorkspaceSettingsPort;
import com.flowops.workspace.application.shared.port.LockWorkspaceStructurePort;
import com.flowops.workspace.application.shared.port.SaveWorkspacePort;
import com.flowops.workspace.application.shared.port.SaveWorkspaceSettingsPort;
import com.flowops.workspace.domain.enums.WorkspaceAction;
import com.flowops.workspace.domain.event.WorkspaceEvent;
import com.flowops.workspace.domain.model.PersonId;
import com.flowops.workspace.domain.model.Workspace;
import com.flowops.workspace.domain.model.WorkspaceId;
import com.flowops.workspace.domain.model.WorkspaceSettings;
import com.flowops.workspace.infrastructure.persistence.entity.WorkspaceJpaEntity;
import com.flowops.workspace.infrastructure.persistence.mapper.WorkspaceEntityMapper;
import com.flowops.workspace.infrastructure.persistence.repository.WorkspaceEventJpaRepository;
import com.flowops.workspace.infrastructure.persistence.repository.WorkspaceJpaRepository;
import com.flowops.workspace.infrastructure.persistence.repository.WorkspaceSettingsJpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class WorkspacePersistenceAdapter
        implements LoadWorkspacePort,
                SaveWorkspacePort,
                SaveWorkspaceSettingsPort,
                LoadWorkspaceSettingsPort,
                LockWorkspaceStructurePort,
                LoadWorkspaceEventsPort,
                AppendWorkspaceEventPort {
    private final WorkspaceJpaRepository workspaces;
    private final WorkspaceSettingsJpaRepository settings;
    private final WorkspaceEventJpaRepository events;
    private final WorkspaceEntityMapper mapper;

    public WorkspacePersistenceAdapter(
            WorkspaceJpaRepository workspaces,
            WorkspaceSettingsJpaRepository settings,
            WorkspaceEventJpaRepository events,
            WorkspaceEntityMapper mapper) {
        this.workspaces = workspaces;
        this.settings = settings;
        this.events = events;
        this.mapper = mapper;
    }

    @Override
    public List<WorkspaceEvent> reportingLineChangesFor(PersonId person) {
        return events
                .findByActionAndSubjectUserIdOrderByOccurredAtAsc(
                        WorkspaceAction.REPORTING_LINE_CHANGED.name(), person.value())
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Workspace load() {
        return mapper.toDomain(seededRow());
    }

    @Override
    public void save(Workspace workspace) {
        WorkspaceJpaEntity entity = seededRow();
        entity.setName(workspace.name().map(name -> name.value()).orElse(null));
        entity.setWorkspaceUse(workspace.use().map(Enum::name).orElse(null));
        workspaces.save(entity);
    }

    @Override
    public void save(WorkspaceSettings incoming) {
        settings.findByWorkspaceIdAndEffectiveToIsNull(incoming.workspaceId().value())
                .filter(current -> !current.getId().equals(incoming.id().value()))
                .ifPresent(current -> {
                    current.setEffectiveTo(incoming.effectiveFrom());
                    settings.saveAndFlush(current);
                });
        settings.saveAndFlush(mapper.toEntity(incoming));
    }

    @Override
    public Optional<WorkspaceSettings> inForce(WorkspaceId workspaceId) {
        return settings.findByWorkspaceIdAndEffectiveToIsNull(workspaceId.value())
                .map(mapper::toDomain);
    }

    @Override
    public void append(WorkspaceEvent event) {
        events.save(mapper.toEntity(event));
    }

    private WorkspaceJpaEntity seededRow() {
        Optional<WorkspaceJpaEntity> seeded = workspaces.findBySingletonTrue();
        return seeded.orElseThrow(
                () -> new IllegalStateException("the workspace row is seeded by the migration and is missing"));
    }

    @Override
    public void lockForStructuralChange() {
        workspaces.lockTheStructure();
    }
}
