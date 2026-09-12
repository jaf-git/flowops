package com.flowops.task.application.shared.port;

import com.flowops.task.domain.model.PersonId;
import java.util.Set;

public interface ReportingLinePort {
    boolean isWithinScopeOf(PersonId actor, PersonId subject);

    Set<PersonId> subtreeOf(PersonId actor);
}
