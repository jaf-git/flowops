package com.flowops.workspace.application.shared.port;

import com.flowops.workspace.domain.model.EmailAddress;
import com.flowops.workspace.domain.model.PersonId;
import java.util.Optional;

public interface FindPersonByEmailPort {
    Optional<PersonId> findPersonByEmail(EmailAddress email);
}
