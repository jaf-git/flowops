package com.flowops.auth.application.createinvitedaccount;

import com.flowops.auth.application.shared.SessionContext;
import com.flowops.auth.application.shared.SessionMetadataFactory;
import com.flowops.auth.application.shared.port.AppendAuthEventPort;
import com.flowops.auth.application.shared.port.LoadUserPort;
import com.flowops.auth.application.shared.port.ResolvePermissionsPort;
import com.flowops.auth.application.shared.port.SaveCredentialPort;
import com.flowops.auth.application.shared.port.SaveUserPort;
import com.flowops.auth.application.shared.port.SessionRegistryPort;
import com.flowops.auth.domain.enums.AuthAction;
import com.flowops.auth.domain.enums.PasswordRule;
import com.flowops.auth.domain.event.AuthEvent;
import com.flowops.auth.domain.exception.DisplayNameRequiredException;
import com.flowops.auth.domain.model.Credential;
import com.flowops.auth.domain.model.DisplayName;
import com.flowops.auth.domain.model.EmailAddress;
import com.flowops.auth.domain.model.RoleName;
import com.flowops.auth.domain.model.SessionMetadata;
import com.flowops.auth.domain.model.User;
import com.flowops.auth.domain.service.PasswordHasher;
import com.flowops.auth.domain.service.PasswordPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateInvitedAccountService implements CreateInvitedAccountUseCase {
    private final LoadUserPort loadUserPort;
    private final SaveUserPort saveUserPort;
    private final SaveCredentialPort saveCredentialPort;
    private final SessionRegistryPort sessionRegistryPort;
    private final AppendAuthEventPort appendAuthEventPort;
    private final ResolvePermissionsPort resolvePermissionsPort;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicy passwordPolicy;
    private final SessionMetadataFactory sessionMetadataFactory;
    private final Clock clock;

    public CreateInvitedAccountService(
            LoadUserPort loadUserPort,
            SaveUserPort saveUserPort,
            SaveCredentialPort saveCredentialPort,
            SessionRegistryPort sessionRegistryPort,
            AppendAuthEventPort appendAuthEventPort,
            ResolvePermissionsPort resolvePermissionsPort,
            PasswordHasher passwordHasher,
            PasswordPolicy passwordPolicy,
            SessionMetadataFactory sessionMetadataFactory,
            Clock clock) {
        this.loadUserPort = loadUserPort;
        this.saveUserPort = saveUserPort;
        this.saveCredentialPort = saveCredentialPort;
        this.sessionRegistryPort = sessionRegistryPort;
        this.appendAuthEventPort = appendAuthEventPort;
        this.resolvePermissionsPort = resolvePermissionsPort;
        this.passwordHasher = passwordHasher;
        this.passwordPolicy = passwordPolicy;
        this.sessionMetadataFactory = sessionMetadataFactory;
        this.clock = clock;
    }

    @Override
    @Transactional
    public InvitedAccountCreated execute(CreateInvitedAccountCommand command) {
        EmailAddress email = new EmailAddress(command.email());
        DisplayName displayName = nameGiven(command.displayName());

        if (loadUserPort.existsByEmail(email)) {
            throw new InvitedAddressTakenException();
        }

        String submitted = command.password() == null ? "" : command.password();
        List<PasswordRule> violations = passwordPolicy.violationsFor(submitted, email);
        if (!violations.isEmpty()) {
            throw new InvitedPasswordRefusedException(
                    violations.stream().map(Enum::name).toList());
        }

        Instant now = clock.instant();
        SessionMetadata metadata = sessionMetadataFactory.from(command.clientContext());

        User joined = User.registerInvited(email, displayName, new RoleName(command.role()), now);
        saveUserPort.save(joined);
        saveCredentialPort.save(Credential.issue(joined.id(), submitted, passwordHasher, now));
        sessionRegistryPort.open(joined, metadata);
        appendAuthEventPort.append(AuthEvent.byActor(AuthAction.INVITED_ACCOUNT_CREATED, joined.id(), now, metadata));

        SessionContext context = SessionContext.of(joined, resolvePermissionsPort.resolveFor(joined.id()));
        return new InvitedAccountCreated(
                context.userId(),
                context.email(),
                context.accountState().name(),
                context.permissions(),
                context.landingTarget().name());
    }

    private static DisplayName nameGiven(String submitted) {
        try {
            return new DisplayName(submitted);
        } catch (DisplayNameRequiredException refused) {
            throw new InvitedNameRequiredException();
        }
    }
}
