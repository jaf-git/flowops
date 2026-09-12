package com.flowops.workspace.application.shared.port;

import com.flowops.workspace.domain.model.PersonId;

public interface AnonymisePersonPort {
    void anonymise(PersonId person);
}
