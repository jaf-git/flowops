package com.flowops.auth.application.changepassword;

import com.flowops.auth.application.shared.SensitiveActionGuard;
import com.flowops.auth.application.shared.SessionMetadataFactory;
import com.flowops.auth.application.shared.exception.AuthenticationRefusedException;
import com.flowops.auth.application.shared.port.AppendAuthEventPort;
import com.flowops.auth.application.shared.port.LoadCredentialPort;
import com.flowops.auth.application.shared.port.LoadUserPort;
import com.flowops.auth.application.shared.port.SaveCredentialPort;
import com.flowops.auth.application.shared.port.SessionRegistryPort;
import com.flowops.auth.domain.enums.AuthAction;
import com.flowops.auth.domain.enums.PasswordRule;
import com.flowops.auth.domain.event.AuthEvent;
import com.flowops.auth.domain.exception.PasswordPolicyViolationException;
import com.flowops.auth.domain.model.Credential;
import com.flowops.auth.domain.model.SessionMetadata;
import com.flowops.auth.domain.model.User;
import com.flowops.auth.domain.model.UserId;
import com.flowops.auth.domain.service.PasswordHasher;
import com.flowops.auth.domain.service.PasswordPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChangePasswordService implements ChangePasswordUseCase {
    private final SessionRegistryPort sessionRegistryPort;
    private final LoadUserPort loadUserPort;
    private final LoadCredentialPort loadCredentialPort;
    private final SaveCredentialPort saveCredentialPort;
    private final AppendAuthEventPort appendAuthEventPort;
    private final SensitiveActionGuard sensitiveActionGuard;
    private final SessionMetadataFactory sessionMetadataFactory;
    private final PasswordPolicy passwordPolicy;
    private final PasswordHasher passwordHasher;
    private final Clock clock;

    public ChangePasswordService(
            SessionRegistryPort sessionRegistryPort,
            LoadUserPort loadUserPort,
            LoadCredentialPort loadCredentialPort,
            SaveCredentialPort saveCredentialPort,
            AppendAuthEventPort appendAuthEventPort,
            SensitiveActionGuard sensitiveActionGuard,
            SessionMetadataFactory sessionMetadataFactory,
            PasswordPolicy passwordPolicy,
            PasswordHasher passwordHasher,
            Clock clock) {
        this.sessionRegistryPort = sessionRegistryPort;
        this.loadUserPort = loadUserPort;
        this.loadCredentialPort = loadCredentialPort;
        this.saveCredentialPort = saveCredentialPort;
        this.appendAuthEventPort = appendAuthEventPort;
        this.sensitiveActionGuard = sensitiveActionGuard;
        this.sessionMetadataFactory = sessionMetadataFactory;
        this.passwordPolicy = passwordPolicy;
        this.passwordHasher = passwordHasher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void execute(ChangePasswordCommand command) {
        UserId userId = sessionRegistryPort.currentUserId().orElseThrow(AuthenticationRefusedException::new);

        sensitiveActionGuard.requireRecentReauthentication();

        User user = loadUserPort.loadById(userId).orElseThrow(AuthenticationRefusedException::new);
        Credential credential = loadCredentialPort.loadFor(userId).orElseThrow(AuthenticationRefusedException::new);

        List<PasswordRule> violations = passwordPolicy.violationsForChange(
                command.newPassword(), user.email(), credential.matches(command.newPassword(), passwordHasher));
        if (!violations.isEmpty()) {
            throw new PasswordPolicyViolationException(violations);
        }

        Instant now = clock.instant();
        SessionMetadata metadata = sessionMetadataFactory.from(command.clientContext());
        saveCredentialPort.save(credential.replaceWith(command.newPassword(), passwordHasher, now));

        sessionRegistryPort.endEverySessionFor(userId);
        sessionRegistryPort.open(user, metadata);

        appendAuthEventPort.append(AuthEvent.byActor(AuthAction.PASSWORD_CHANGED, userId, now, metadata));
    }
}
