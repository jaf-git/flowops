package com.flowops.auth.application.logout;

import com.flowops.auth.application.shared.SessionMetadataFactory;
import com.flowops.auth.application.shared.port.AppendAuthEventPort;
import com.flowops.auth.application.shared.port.SessionRegistryPort;
import com.flowops.auth.domain.enums.AuthAction;
import com.flowops.auth.domain.event.AuthEvent;
import com.flowops.auth.domain.model.UserId;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LogoutService implements LogoutUseCase {
    private final SessionRegistryPort sessionRegistryPort;
    private final AppendAuthEventPort appendAuthEventPort;
    private final SessionMetadataFactory sessionMetadataFactory;
    private final Clock clock;

    public LogoutService(
            SessionRegistryPort sessionRegistryPort,
            AppendAuthEventPort appendAuthEventPort,
            SessionMetadataFactory sessionMetadataFactory,
            Clock clock) {
        this.sessionRegistryPort = sessionRegistryPort;
        this.appendAuthEventPort = appendAuthEventPort;
        this.sessionMetadataFactory = sessionMetadataFactory;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void execute(LogoutCommand command) {
        Optional<UserId> current = sessionRegistryPort.currentUserId();
        if (current.isEmpty()) {
            return;
        }
        appendAuthEventPort.append(AuthEvent.byActor(
                AuthAction.LOGGED_OUT,
                current.get(),
                clock.instant(),
                sessionMetadataFactory.from(command.clientContext())));
        sessionRegistryPort.endCurrent();
    }
}
