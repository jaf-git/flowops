package com.flowops.workspace.application.shared.port;

import com.flowops.workspace.domain.model.PersonId;

public interface EndPersonSessionsPort {
    void endEverySessionFor(PersonId person);
}
