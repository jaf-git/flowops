package com.flowops.auth.application.viewsessioncontext;

import com.flowops.auth.application.shared.SessionContext;
import com.flowops.auth.application.shared.port.LoadUserPort;
import com.flowops.auth.application.shared.port.ResolvePermissionsPort;
import com.flowops.auth.application.shared.port.SessionRegistryPort;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ViewSessionContextService implements ViewSessionContextUseCase {
    private final SessionRegistryPort sessionRegistryPort;
    private final LoadUserPort loadUserPort;
    private final ResolvePermissionsPort resolvePermissionsPort;

    public ViewSessionContextService(
            SessionRegistryPort sessionRegistryPort,
            LoadUserPort loadUserPort,
            ResolvePermissionsPort resolvePermissionsPort) {
        this.sessionRegistryPort = sessionRegistryPort;
        this.loadUserPort = loadUserPort;
        this.resolvePermissionsPort = resolvePermissionsPort;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SessionContext> execute() {
        return sessionRegistryPort
                .currentUserId()
                .flatMap(loadUserPort::loadById)
                .map(user -> SessionContext.of(user, resolvePermissionsPort.resolveFor(user.id())));
    }
}
