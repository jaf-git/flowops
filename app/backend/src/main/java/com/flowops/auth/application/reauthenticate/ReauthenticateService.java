package com.flowops.auth.application.reauthenticate;

import com.flowops.auth.application.shared.AttemptPurpose;
import com.flowops.auth.application.shared.AuthProperties;
import com.flowops.auth.application.shared.SessionMetadataFactory;
import com.flowops.auth.application.shared.exception.AuthenticationRefusedException;
import com.flowops.auth.application.shared.exception.RateLimitExceededException;
import com.flowops.auth.application.shared.port.AppendAuthEventPort;
import com.flowops.auth.application.shared.port.LoadCredentialPort;
import com.flowops.auth.application.shared.port.RateLimitPort;
import com.flowops.auth.application.shared.port.SessionRegistryPort;
import com.flowops.auth.domain.enums.AuthAction;
import com.flowops.auth.domain.event.AuthEvent;
import com.flowops.auth.domain.model.SessionMetadata;
import com.flowops.auth.domain.model.UserId;
import com.flowops.auth.domain.service.PasswordHasher;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReauthenticateService implements ReauthenticateUseCase {
    private final SessionRegistryPort sessionRegistryPort;
    private final LoadCredentialPort loadCredentialPort;
    private final AppendAuthEventPort appendAuthEventPort;
    private final RateLimitPort rateLimitPort;
    private final SessionMetadataFactory sessionMetadataFactory;
    private final PasswordHasher passwordHasher;
    private final AuthProperties authProperties;
    private final Clock clock;

    private final String absentCredentialHash;

    public ReauthenticateService(
            SessionRegistryPort sessionRegistryPort,
            LoadCredentialPort loadCredentialPort,
            AppendAuthEventPort appendAuthEventPort,
            RateLimitPort rateLimitPort,
            SessionMetadataFactory sessionMetadataFactory,
            PasswordHasher passwordHasher,
            AuthProperties authProperties,
            Clock clock) {
        this.sessionRegistryPort = sessionRegistryPort;
        this.loadCredentialPort = loadCredentialPort;
        this.appendAuthEventPort = appendAuthEventPort;
        this.rateLimitPort = rateLimitPort;
        this.sessionMetadataFactory = sessionMetadataFactory;
        this.passwordHasher = passwordHasher;
        this.authProperties = authProperties;
        this.clock = clock;
        this.absentCredentialHash = passwordHasher.hash(UUID.randomUUID().toString());
    }

    @Override
    @Transactional(noRollbackFor = AuthenticationRefusedException.class)
    public Instant execute(ReauthenticateCommand command) {
        UserId userId = sessionRegistryPort.currentUserId().orElseThrow(AuthenticationRefusedException::new);
        SessionMetadata metadata = sessionMetadataFactory.from(command.clientContext());
        Instant now = clock.instant();

        if (rateLimitPort.isLimited(
                AttemptPurpose.REAUTHENTICATION, userId.value().toString(), metadata.ipAddress())) {
            throw new RateLimitExceededException();
        }

        if (!verified(userId, command.password())) {
            appendAuthEventPort.append(AuthEvent.byActor(AuthAction.REAUTHENTICATION_FAILED, userId, now, metadata));
            rateLimitPort.record(AttemptPurpose.REAUTHENTICATION, userId.value().toString(), metadata.ipAddress());
            throw new AuthenticationRefusedException();
        }

        Instant until = now.plus(authProperties.reauthenticationWindow());
        sessionRegistryPort.markReauthenticated(until);
        appendAuthEventPort.append(AuthEvent.byActor(AuthAction.REAUTHENTICATION_SUCCEEDED, userId, now, metadata));
        return until;
    }

    private boolean verified(UserId userId, String password) {
        return loadCredentialPort
                .loadFor(userId)
                .map(credential -> credential.matches(password, passwordHasher))
                .orElseGet(() -> {
                    passwordHasher.matches(password, absentCredentialHash);
                    return false;
                });
    }
}
