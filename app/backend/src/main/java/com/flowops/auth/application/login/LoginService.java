package com.flowops.auth.application.login;

import com.flowops.auth.application.shared.AttemptPurpose;
import com.flowops.auth.application.shared.SessionContext;
import com.flowops.auth.application.shared.SessionMetadataFactory;
import com.flowops.auth.application.shared.exception.AuthenticationRefusedException;
import com.flowops.auth.application.shared.exception.RateLimitExceededException;
import com.flowops.auth.application.shared.port.AppendAuthEventPort;
import com.flowops.auth.application.shared.port.LoadCredentialPort;
import com.flowops.auth.application.shared.port.LoadUserPort;
import com.flowops.auth.application.shared.port.RateLimitPort;
import com.flowops.auth.application.shared.port.ResolvePermissionsPort;
import com.flowops.auth.application.shared.port.SessionRegistryPort;
import com.flowops.auth.domain.enums.AuthAction;
import com.flowops.auth.domain.event.AuthEvent;
import com.flowops.auth.domain.model.Credential;
import com.flowops.auth.domain.model.EmailAddress;
import com.flowops.auth.domain.model.SessionMetadata;
import com.flowops.auth.domain.model.User;
import com.flowops.auth.domain.model.UserId;
import com.flowops.auth.domain.service.PasswordHasher;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginService implements LoginUseCase {
    private final LoadUserPort loadUserPort;
    private final LoadCredentialPort loadCredentialPort;
    private final SessionRegistryPort sessionRegistryPort;
    private final ResolvePermissionsPort resolvePermissionsPort;
    private final AppendAuthEventPort appendAuthEventPort;
    private final RateLimitPort rateLimitPort;
    private final SessionMetadataFactory sessionMetadataFactory;
    private final PasswordHasher passwordHasher;
    private final Clock clock;

    private final String absentCredentialHash;

    public LoginService(
            LoadUserPort loadUserPort,
            LoadCredentialPort loadCredentialPort,
            SessionRegistryPort sessionRegistryPort,
            ResolvePermissionsPort resolvePermissionsPort,
            AppendAuthEventPort appendAuthEventPort,
            RateLimitPort rateLimitPort,
            SessionMetadataFactory sessionMetadataFactory,
            PasswordHasher passwordHasher,
            Clock clock) {
        this.loadUserPort = loadUserPort;
        this.loadCredentialPort = loadCredentialPort;
        this.sessionRegistryPort = sessionRegistryPort;
        this.resolvePermissionsPort = resolvePermissionsPort;
        this.appendAuthEventPort = appendAuthEventPort;
        this.rateLimitPort = rateLimitPort;
        this.sessionMetadataFactory = sessionMetadataFactory;
        this.passwordHasher = passwordHasher;
        this.clock = clock;
        this.absentCredentialHash = passwordHasher.hash(UUID.randomUUID().toString());
    }

    @Override
    @Transactional(noRollbackFor = AuthenticationRefusedException.class)
    public SessionContext execute(LoginCommand command) {
        EmailAddress email = new EmailAddress(command.email());
        SessionMetadata metadata = sessionMetadataFactory.from(command.clientContext());
        Instant now = clock.instant();

        if (rateLimitPort.isLimited(AttemptPurpose.LOGIN, email.value(), metadata.ipAddress())) {
            throw new RateLimitExceededException();
        }

        User user = loadUserPort.loadByEmail(email).orElse(null);
        if (!authenticates(user, command.password())) {
            throw refusalFor(email, metadata, now);
        }

        sessionRegistryPort.open(user, metadata);
        appendAuthEventPort.append(AuthEvent.byActor(AuthAction.LOGIN_SUCCEEDED, user.id(), now, metadata));
        return SessionContext.of(user, resolvePermissionsPort.resolveFor(user.id()));
    }

    private boolean authenticates(User user, String password) {
        Optional<Credential> credential = loadCredentialPort.loadFor(user == null ? UserId.generate() : user.id());
        boolean passwordMatches = credential
                .map(held -> held.matches(password, passwordHasher))
                .orElseGet(() -> {
                    passwordHasher.matches(password, absentCredentialHash);
                    return false;
                });
        return passwordMatches && user.canAuthenticate();
    }

    private AuthenticationRefusedException refusalFor(EmailAddress email, SessionMetadata metadata, Instant now) {
        appendAuthEventPort.append(AuthEvent.forSubject(AuthAction.LOGIN_FAILED, email, now, metadata));
        rateLimitPort.record(AttemptPurpose.LOGIN, email.value(), metadata.ipAddress());
        return new AuthenticationRefusedException();
    }
}
