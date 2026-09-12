package com.flowops.auth.application.viewownsessions;

import com.flowops.auth.application.shared.exception.AuthenticationRefusedException;
import com.flowops.auth.application.shared.port.SessionRegistryPort;
import com.flowops.auth.domain.model.ActiveSession;
import com.flowops.auth.domain.model.UserId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ViewOwnSessionsService implements ViewOwnSessionsUseCase {
    private final SessionRegistryPort sessionRegistryPort;

    public ViewOwnSessionsService(SessionRegistryPort sessionRegistryPort) {
        this.sessionRegistryPort = sessionRegistryPort;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActiveSession> execute() {
        UserId caller = sessionRegistryPort.currentUserId().orElseThrow(AuthenticationRefusedException::new);
        return sessionRegistryPort.sessionsOf(caller);
    }
}
