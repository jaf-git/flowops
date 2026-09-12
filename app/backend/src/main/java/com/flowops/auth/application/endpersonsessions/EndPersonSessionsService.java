package com.flowops.auth.application.endpersonsessions;

import com.flowops.auth.application.shared.port.SessionRegistryPort;
import com.flowops.auth.domain.model.UserId;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class EndPersonSessionsService implements EndPersonSessionsUseCase {
    private final SessionRegistryPort sessionRegistryPort;

    public EndPersonSessionsService(SessionRegistryPort sessionRegistryPort) {
        this.sessionRegistryPort = sessionRegistryPort;
    }

    @Override
    public void execute(UUID personId) {
        sessionRegistryPort.endEverySessionFor(new UserId(personId));
    }
}
