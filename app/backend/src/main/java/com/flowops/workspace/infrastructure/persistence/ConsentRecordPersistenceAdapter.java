package com.flowops.workspace.infrastructure.persistence;

import com.flowops.workspace.application.shared.port.LoadConsentRecordPort;
import com.flowops.workspace.application.shared.port.SaveConsentRecordPort;
import com.flowops.workspace.domain.model.ConsentRecord;
import com.flowops.workspace.domain.model.ConsentRecordId;
import com.flowops.workspace.domain.model.PersonId;
import com.flowops.workspace.infrastructure.persistence.entity.WorkspaceConsentRecordJpaEntity;
import com.flowops.workspace.infrastructure.persistence.repository.WorkspaceConsentRecordJpaRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ConsentRecordPersistenceAdapter implements SaveConsentRecordPort, LoadConsentRecordPort {
    private final WorkspaceConsentRecordJpaRepository records;

    public ConsentRecordPersistenceAdapter(WorkspaceConsentRecordJpaRepository records) {
        this.records = records;
    }

    @Override
    public ConsentRecord save(ConsentRecord record) {
        records.save(new WorkspaceConsentRecordJpaEntity(
                record.id().value(),
                record.person().value(),
                record.language(),
                record.version(),
                record.text(),
                record.agreedAt()));
        return record;
    }

    @Override
    public Optional<ConsentRecord> newestFor(PersonId person) {
        return records.findFirstByUserIdOrderByAgreedAtDesc(person.value())
                .map(entity -> new ConsentRecord(
                        ConsentRecordId.of(entity.getId()),
                        PersonId.of(entity.getUserId()),
                        entity.getLanguage(),
                        entity.getVersion(),
                        entity.getConsentText(),
                        entity.getAgreedAt()));
    }
}
