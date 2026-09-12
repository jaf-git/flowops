package com.flowops.auth.application.viewusersessions;

import com.flowops.auth.application.shared.SessionMetadataFactory;
import com.flowops.auth.application.shared.exception.AuthenticationRefusedException;
import com.flowops.auth.application.shared.port.AppendAuthEventPort;
import com.flowops.auth.application.shared.port.SessionRegistryPort;
import com.flowops.auth.domain.enums.AuthAction;
import com.flowops.auth.domain.event.AuthEvent;
import com.flowops.auth.domain.model.ActiveSession;
import com.flowops.auth.domain.model.UserId;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ViewUserSessionsService implements ViewUserSessionsUseCase {
    private final SessionRegistryPort sessionRegistryPort;
    private final AppendAuthEventPort appendAuthEventPort;
    private final SessionMetadataFactory sessionMetadataFactory;
    private final Clock clock;

    public ViewUserSessionsService(
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
    public List<ActiveSession> execute(ViewUserSessionsQuery query) {
        UserId actor = sessionRegistryPort.currentUserId().orElseThrow(AuthenticationRefusedException::new);
        List<ActiveSession> theirs = sessionRegistryPort.sessionsOf(query.subject());

        appendAuthEventPort.append(AuthEvent.byActorUpon(
                AuthAction.SESSIONS_OF_PERSON_LISTED,
                actor,
                query.subject(),
                clock.instant(),
                sessionMetadataFactory.from(query.clientContext())));

        return theirs;
    }
}
