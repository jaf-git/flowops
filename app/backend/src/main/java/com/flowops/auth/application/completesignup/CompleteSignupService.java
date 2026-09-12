package com.flowops.auth.application.completesignup;

import com.flowops.auth.application.shared.AttemptPurpose;
import com.flowops.auth.application.shared.AuthProperties;
import com.flowops.auth.application.shared.SessionContext;
import com.flowops.auth.application.shared.SessionMetadataFactory;
import com.flowops.auth.application.shared.exception.PasscodeAttemptLockedException;
import com.flowops.auth.application.shared.exception.PasscodeRejectedException;
import com.flowops.auth.application.shared.exception.RateLimitExceededException;
import com.flowops.auth.application.shared.exception.SignupClosedException;
import com.flowops.auth.application.shared.port.AppendAuthEventPort;
import com.flowops.auth.application.shared.port.LoadPasscodePort;
import com.flowops.auth.application.shared.port.LoadUserPort;
import com.flowops.auth.application.shared.port.RateLimitPort;
import com.flowops.auth.application.shared.port.ResolvePermissionsPort;
import com.flowops.auth.application.shared.port.SaveCredentialPort;
import com.flowops.auth.application.shared.port.SavePasscodePort;
import com.flowops.auth.application.shared.port.SaveUserPort;
import com.flowops.auth.application.shared.port.SessionRegistryPort;
import com.flowops.auth.domain.enums.AuthAction;
import com.flowops.auth.domain.enums.PasswordRule;
import com.flowops.auth.domain.event.AuthEvent;
import com.flowops.auth.domain.exception.PasswordPolicyViolationException;
import com.flowops.auth.domain.model.Credential;
import com.flowops.auth.domain.model.EmailAddress;
import com.flowops.auth.domain.model.SessionMetadata;
import com.flowops.auth.domain.model.SignupPasscode;
import com.flowops.auth.domain.model.User;
import com.flowops.auth.domain.service.PasswordHasher;
import com.flowops.auth.domain.service.PasswordPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompleteSignupService implements CompleteSignupUseCase {
    private final LoadUserPort loadUserPort;
    private final SaveUserPort saveUserPort;
    private final LoadPasscodePort loadPasscodePort;
    private final SavePasscodePort savePasscodePort;
    private final SaveCredentialPort saveCredentialPort;
    private final SessionRegistryPort sessionRegistryPort;
    private final ResolvePermissionsPort resolvePermissionsPort;
    private final AppendAuthEventPort appendAuthEventPort;
    private final RateLimitPort rateLimitPort;
    private final SessionMetadataFactory sessionMetadataFactory;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicy passwordPolicy;
    private final AuthProperties authProperties;
    private final Clock clock;

    public CompleteSignupService(
            LoadUserPort loadUserPort,
            SaveUserPort saveUserPort,
            LoadPasscodePort loadPasscodePort,
            SavePasscodePort savePasscodePort,
            SaveCredentialPort saveCredentialPort,
            SessionRegistryPort sessionRegistryPort,
            ResolvePermissionsPort resolvePermissionsPort,
            AppendAuthEventPort appendAuthEventPort,
            RateLimitPort rateLimitPort,
            SessionMetadataFactory sessionMetadataFactory,
            PasswordHasher passwordHasher,
            PasswordPolicy passwordPolicy,
            AuthProperties authProperties,
            Clock clock) {
        this.loadUserPort = loadUserPort;
        this.saveUserPort = saveUserPort;
        this.loadPasscodePort = loadPasscodePort;
        this.savePasscodePort = savePasscodePort;
        this.saveCredentialPort = saveCredentialPort;
        this.sessionRegistryPort = sessionRegistryPort;
        this.resolvePermissionsPort = resolvePermissionsPort;
        this.appendAuthEventPort = appendAuthEventPort;
        this.rateLimitPort = rateLimitPort;
        this.sessionMetadataFactory = sessionMetadataFactory;
        this.passwordHasher = passwordHasher;
        this.passwordPolicy = passwordPolicy;
        this.authProperties = authProperties;
        this.clock = clock;
    }

    @Override
    @Transactional(noRollbackFor = {PasscodeRejectedException.class, PasscodeAttemptLockedException.class})
    public SessionContext execute(CompleteSignupCommand command) {
        if (loadUserPort.anOwnerExists()) {
            throw new SignupClosedException();
        }

        EmailAddress email = new EmailAddress(command.email());
        SessionMetadata metadata = sessionMetadataFactory.from(command.clientContext());
        Instant now = clock.instant();

        if (rateLimitPort.isLimited(AttemptPurpose.SIGNUP_COMPLETION, email.value(), metadata.ipAddress())) {
            throw new RateLimitExceededException();
        }

        SignupPasscode passcode = verifiedPasscode(email, command.passcode(), metadata, now);

        List<PasswordRule> violations = passwordPolicy.violationsFor(command.password(), email);
        if (!violations.isEmpty()) {
            throw new PasswordPolicyViolationException(violations);
        }

        User owner = User.registerOwner(email, now);
        saveUserPort.save(owner);
        saveCredentialPort.save(Credential.issue(owner.id(), command.password(), passwordHasher, now));
        savePasscodePort.save(passcode.markUsed());

        sessionRegistryPort.open(owner, metadata);
        appendAuthEventPort.append(AuthEvent.byActor(AuthAction.SIGNUP_COMPLETED, owner.id(), now, metadata));

        return SessionContext.of(owner, resolvePermissionsPort.resolveFor(owner.id()));
    }

    private SignupPasscode verifiedPasscode(
            EmailAddress email, String submitted, SessionMetadata metadata, Instant now) {
        Optional<SignupPasscode> found = loadPasscodePort.loadLatestFor(email);
        if (found.isEmpty()) {
            recordFailedAttempt(email, metadata, now);
            throw new PasscodeRejectedException();
        }

        SignupPasscode passcode = found.get();
        if (passcode.isLocked(authProperties.passcodeFailureCeiling())) {
            recordFailedAttempt(email, metadata, now);
            throw new PasscodeAttemptLockedException();
        }
        if (!passcode.isUsable(now, authProperties.passcodeFailureCeiling())
                || !passcode.matches(submitted, passwordHasher)) {
            SignupPasscode failed = passcode.recordFailure();
            savePasscodePort.save(failed);
            recordFailedAttempt(email, metadata, now);
            if (failed.isLocked(authProperties.passcodeFailureCeiling())) {
                throw new PasscodeAttemptLockedException();
            }
            throw new PasscodeRejectedException();
        }

        if (loadUserPort.existsByEmail(email)) {
            recordFailedAttempt(email, metadata, now);
            throw new PasscodeRejectedException();
        }
        return passcode;
    }

    private void recordFailedAttempt(EmailAddress email, SessionMetadata metadata, Instant now) {
        appendAuthEventPort.append(AuthEvent.forSubject(AuthAction.SIGNUP_FAILED, email, now, metadata));
        rateLimitPort.record(AttemptPurpose.SIGNUP_COMPLETION, email.value(), metadata.ipAddress());
    }
}
