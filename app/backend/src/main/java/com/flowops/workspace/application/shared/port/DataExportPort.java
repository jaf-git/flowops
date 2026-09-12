package com.flowops.workspace.application.shared.port;

import com.flowops.workspace.domain.model.PersonId;
import java.time.Instant;

public interface DataExportPort {
    void record(PersonId subject, PersonId producedBy, Instant at);

    int countProducedSince(PersonId subject, Instant since);
}
