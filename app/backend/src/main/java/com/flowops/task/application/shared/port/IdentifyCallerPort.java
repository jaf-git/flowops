package com.flowops.task.application.shared.port;

import com.flowops.task.domain.model.PersonId;
import java.util.Optional;

public interface IdentifyCallerPort {
    Optional<PersonId> currentCaller();
}
