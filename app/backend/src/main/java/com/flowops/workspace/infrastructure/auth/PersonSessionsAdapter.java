package com.flowops.workspace.infrastructure.auth;

import com.flowops.auth.application.endpersonsessions.EndPersonSessionsUseCase;
import com.flowops.workspace.application.shared.port.EndPersonSessionsPort;
import com.flowops.workspace.domain.model.PersonId;
import org.springframework.stereotype.Component;

@Component
public class PersonSessionsAdapter implements EndPersonSessionsPort {
    private final EndPersonSessionsUseCase endPersonSessions;

    public PersonSessionsAdapter(EndPersonSessionsUseCase endPersonSessions) {
        this.endPersonSessions = endPersonSessions;
    }

    @Override
    public void endEverySessionFor(PersonId person) {
        endPersonSessions.execute(person.value());
    }
}
