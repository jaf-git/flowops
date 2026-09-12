package com.flowops.auth.application.viewsetupstate;

import com.flowops.auth.application.shared.port.LoadUserPort;
import com.flowops.auth.application.shared.port.SessionRegistryPort;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ViewSetupStateService implements ViewSetupStateUseCase {
    private final SessionRegistryPort sessionRegistryPort;
    private final LoadUserPort loadUserPort;

    public ViewSetupStateService(SessionRegistryPort sessionRegistryPort, LoadUserPort loadUserPort) {
        this.sessionRegistryPort = sessionRegistryPort;
        this.loadUserPort = loadUserPort;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SetupState> execute() {
        return sessionRegistryPort
                .currentUserId()
                .flatMap(loadUserPort::loadById)
                .map(SetupState::of);
    }
}
