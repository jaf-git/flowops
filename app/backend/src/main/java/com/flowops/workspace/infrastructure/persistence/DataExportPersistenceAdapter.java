package com.flowops.workspace.infrastructure.persistence;

import com.flowops.workspace.application.shared.port.DataExportPort;
import com.flowops.workspace.domain.model.PersonId;
import com.flowops.workspace.infrastructure.persistence.entity.WorkspaceDataExportJpaEntity;
import com.flowops.workspace.infrastructure.persistence.repository.WorkspaceDataExportJpaRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DataExportPersistenceAdapter implements DataExportPort {
    private final WorkspaceDataExportJpaRepository exports;

    public DataExportPersistenceAdapter(WorkspaceDataExportJpaRepository exports) {
        this.exports = exports;
    }

    @Override
    public void record(PersonId subject, PersonId producedBy, Instant at) {
        exports.save(new WorkspaceDataExportJpaEntity(UUID.randomUUID(), subject.value(), producedBy.value(), at));
    }

    @Override
    public int countProducedSince(PersonId subject, Instant since) {
        return exports.countBySubjectUserIdAndProducedAtAfter(subject.value(), since);
    }
}
