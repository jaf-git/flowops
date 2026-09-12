package com.flowops.auth.application.terminatesession;

import com.flowops.auth.application.shared.SensitiveActionGuard;
import com.flowops.auth.application.shared.SessionMetadataFactory;
import com.flowops.auth.application.shared.exception.AuthenticationRefusedException;
import com.flowops.auth.application.shared.port.AppendAuthEventPort;
import com.flowops.auth.application.shared.port.SessionRegistryPort;
import com.flowops.auth.domain.enums.AuthAction;
import com.flowops.auth.domain.event.AuthEvent;
import com.flowops.auth.domain.model.SessionMetadata;
import com.flowops.auth.domain.model.UserId;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TerminateSessionService implements TerminateSessionUseCase {
    private final SessionRegistryPort sessionRegistryPort;
    private final AppendAuthEventPort appendAuthEventPort;
    private final SensitiveActionGuard sensitiveActionGuard;
    private final SessionMetadataFactory sessionMetadataFactory;
    private final Clock clock;

    public TerminateSessionService(
            SessionRegistryPort sessionRegistryPort,
            AppendAuthEventPort appendAuthEventPort,
            SensitiveActionGuard sensitiveActionGuard,
            SessionMetadataFactory sessionMetadataFactory,
            Clock clock) {
        this.sessionRegistryPort = sessionRegistryPort;
        this.appendAuthEventPort = appendAuthEventPort;
        this.sensitiveActionGuard = sensitiveActionGuard;
        this.sessionMetadataFactory = sessionMetadataFactory;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void execute(TerminateSessionCommand command) {
        UserId actor = sessionRegistryPort.currentUserId().orElseThrow(AuthenticationRefusedException::new);

        sensitiveActionGuard.requireRecentReauthentication();

        Optional<UserId> namedPerson = sessionRegistryPort.ownerOf(command.reference());
        Optional<UserId> endedPersonsSession = sessionRegistryPort.endByReference(command.reference());
        Instant now = clock.instant();
        SessionMetadata metadata = sessionMetadataFactory.from(command.clientContext());

        if (endedPersonsSession.isEmpty()) {
            appendAuthEventPort.append(AuthEvent.byActorUpon(
                    AuthAction.SESSION_TERMINATION_ATTEMPTED, actor, namedPerson.orElse(null), now, metadata));
            return;
        }

        appendAuthEventPort.append(
                AuthEvent.byActorUpon(AuthAction.SESSION_TERMINATED, actor, endedPersonsSession.get(), now, metadata));
    }
}
