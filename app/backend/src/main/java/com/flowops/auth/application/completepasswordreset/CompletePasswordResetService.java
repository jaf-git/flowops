package com.flowops.auth.application.completepasswordreset;

import com.flowops.auth.application.shared.SessionMetadataFactory;
import com.flowops.auth.application.shared.UsableResetTokens;
import com.flowops.auth.application.shared.exception.ResetTokenNotUsableException;
import com.flowops.auth.application.shared.port.AppendAuthEventPort;
import com.flowops.auth.application.shared.port.LoadCredentialPort;
import com.flowops.auth.application.shared.port.SaveCredentialPort;
import com.flowops.auth.application.shared.port.SaveResetTokenPort;
import com.flowops.auth.application.shared.port.SessionRegistryPort;
import com.flowops.auth.domain.enums.AuthAction;
import com.flowops.auth.domain.enums.PasswordRule;
import com.flowops.auth.domain.event.AuthEvent;
import com.flowops.auth.domain.exception.PasswordPolicyViolationException;
import com.flowops.auth.domain.model.Credential;
import com.flowops.auth.domain.model.SessionMetadata;
import com.flowops.auth.domain.service.PasswordHasher;
import com.flowops.auth.domain.service.PasswordPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompletePasswordResetService implements CompletePasswordResetUseCase {
    private final UsableResetTokens usableResetTokens;
    private final SaveResetTokenPort saveResetTokenPort;
    private final LoadCredentialPort loadCredentialPort;
    private final SaveCredentialPort saveCredentialPort;
    private final SessionRegistryPort sessionRegistryPort;
    private final AppendAuthEventPort appendAuthEventPort;
    private final SessionMetadataFactory sessionMetadataFactory;
    private final PasswordPolicy passwordPolicy;
    private final PasswordHasher passwordHasher;
    private final Clock clock;

    public CompletePasswordResetService(
            UsableResetTokens usableResetTokens,
            SaveResetTokenPort saveResetTokenPort,
            LoadCredentialPort loadCredentialPort,
            SaveCredentialPort saveCredentialPort,
            SessionRegistryPort sessionRegistryPort,
            AppendAuthEventPort appendAuthEventPort,
            SessionMetadataFactory sessionMetadataFactory,
            PasswordPolicy passwordPolicy,
            PasswordHasher passwordHasher,
            Clock clock) {
        this.usableResetTokens = usableResetTokens;
        this.saveResetTokenPort = saveResetTokenPort;
        this.loadCredentialPort = loadCredentialPort;
        this.saveCredentialPort = saveCredentialPort;
        this.sessionRegistryPort = sessionRegistryPort;
        this.appendAuthEventPort = appendAuthEventPort;
        this.sessionMetadataFactory = sessionMetadataFactory;
        this.passwordPolicy = passwordPolicy;
        this.passwordHasher = passwordHasher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void execute(CompletePasswordResetCommand command) {
        Instant now = clock.instant();
        UsableResetTokens.Resolved resolved = usableResetTokens.resolve(command.clearToken(), now);

        Credential credential =
                loadCredentialPort.loadFor(resolved.user().id()).orElseThrow(ResetTokenNotUsableException::new);

        List<PasswordRule> violations = passwordPolicy.violationsForChange(
                command.newPassword(),
                resolved.user().email(),
                credential.matches(command.newPassword(), passwordHasher));
        if (!violations.isEmpty()) {
            throw new PasswordPolicyViolationException(violations);
        }

        SessionMetadata metadata = sessionMetadataFactory.from(command.clientContext());

        saveCredentialPort.save(credential.replaceWith(command.newPassword(), passwordHasher, now));
        saveResetTokenPort.save(resolved.token().spendAt(now));
        sessionRegistryPort.endEverySessionFor(resolved.user().id());
        appendAuthEventPort.append(AuthEvent.byActor(
                AuthAction.PASSWORD_RESET_COMPLETED, resolved.user().id(), now, metadata));
    }
}
