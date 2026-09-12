package com.flowops.auth.application.terminatesession;

import com.flowops.auth.application.shared.SessionMetadataFactory;
import com.flowops.auth.application.shared.port.AppendAuthEventPort;
import com.flowops.auth.application.shared.port.SessionRegistryPort;
import com.flowops.auth.domain.event.AuthEvent;
import com.flowops.auth.domain.model.SessionMetadata;
import com.flowops.auth.domain.model.UserId;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecordDeniedAttemptService implements RecordDeniedAttemptUseCase {
    private final SessionRegistryPort sessionRegistryPort;
    private final AppendAuthEventPort appendAuthEventPort;
    private final SessionMetadataFactory sessionMetadataFactory;
    private final Clock clock;

    public RecordDeniedAttemptService(
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void execute(RecordDeniedAttemptCommand command) {
        UserId actor = sessionRegistryPort.currentUserId().orElse(null);
        SessionMetadata metadata = sessionMetadataFactory.from(command.clientContext());

        UserId target = command.namedSubject()
                .or(() -> command.attemptedSessionReference().flatMap(sessionRegistryPort::ownerOf))
                .orElse(null);

        appendAuthEventPort.append(AuthEvent.byActorUpon(command.action(), actor, target, clock.instant(), metadata));
    }
}
