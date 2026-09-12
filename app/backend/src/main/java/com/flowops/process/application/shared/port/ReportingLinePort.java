package com.flowops.process.application.shared.port;

import com.flowops.process.domain.model.PersonId;
import java.util.Set;

public interface ReportingLinePort {
    Set<PersonId> subtreeOf(PersonId actor);
}
