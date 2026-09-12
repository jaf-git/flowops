package com.flowops.process.application.shared.port;

import com.flowops.process.domain.model.PersonId;
import java.util.Optional;

public interface IdentifyCallerPort {
    Optional<PersonId> currentCaller();
}
