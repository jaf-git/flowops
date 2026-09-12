package com.flowops.workspace.application.shared.port;

import com.flowops.workspace.domain.model.ConsentRecord;
import com.flowops.workspace.domain.model.PersonId;
import java.util.Optional;

public interface LoadConsentRecordPort {
    Optional<ConsentRecord> newestFor(PersonId person);
}
