package com.flowops.auth.application.requestsignuppasscode;

import com.flowops.auth.application.shared.AttemptPurpose;
import com.flowops.auth.application.shared.AuthProperties;
import com.flowops.auth.application.shared.SessionMetadataFactory;
import com.flowops.auth.application.shared.exception.RateLimitExceededException;
import com.flowops.auth.application.shared.exception.SignupClosedException;
import com.flowops.auth.application.shared.port.AppendAuthEventPort;
import com.flowops.auth.application.shared.port.GeneratePasscodePort;
import com.flowops.auth.application.shared.port.LoadUserPort;
import com.flowops.auth.application.shared.port.RateLimitPort;
import com.flowops.auth.application.shared.port.SavePasscodePort;
import com.flowops.auth.application.shared.port.SendPasscodePort;
import com.flowops.auth.domain.enums.AuthAction;
import com.flowops.auth.domain.event.AuthEvent;
import com.flowops.auth.domain.model.EmailAddress;
import com.flowops.auth.domain.model.SessionMetadata;
import com.flowops.auth.domain.model.SignupPasscode;
import com.flowops.auth.domain.service.PasswordHasher;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestSignupPasscodeService implements RequestSignupPasscodeUseCase {
    private final LoadUserPort loadUserPort;
    private final SavePasscodePort savePasscodePort;
    private final GeneratePasscodePort generatePasscodePort;
    private final AppendAuthEventPort appendAuthEventPort;
    private final SendPasscodePort sendPasscodePort;
    private final RateLimitPort rateLimitPort;
    private final SessionMetadataFactory sessionMetadataFactory;
    private final PasswordHasher passwordHasher;
    private final AuthProperties authProperties;
    private final Clock clock;

    public RequestSignupPasscodeService(
            LoadUserPort loadUserPort,
            SavePasscodePort savePasscodePort,
            GeneratePasscodePort generatePasscodePort,
            AppendAuthEventPort appendAuthEventPort,
            SendPasscodePort sendPasscodePort,
            RateLimitPort rateLimitPort,
            SessionMetadataFactory sessionMetadataFactory,
            PasswordHasher passwordHasher,
            AuthProperties authProperties,
            Clock clock) {
        this.loadUserPort = loadUserPort;
        this.savePasscodePort = savePasscodePort;
        this.generatePasscodePort = generatePasscodePort;
        this.appendAuthEventPort = appendAuthEventPort;
        this.sendPasscodePort = sendPasscodePort;
        this.rateLimitPort = rateLimitPort;
        this.sessionMetadataFactory = sessionMetadataFactory;
        this.passwordHasher = passwordHasher;
        this.authProperties = authProperties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void execute(RequestSignupPasscodeCommand command) {
        if (loadUserPort.anOwnerExists()) {
            throw new SignupClosedException();
        }

        EmailAddress email = new EmailAddress(command.email());
        SessionMetadata metadata = sessionMetadataFactory.from(command.clientContext());
        Instant now = clock.instant();

        if (rateLimitPort.isLimited(AttemptPurpose.SIGNUP, email.value(), metadata.ipAddress())) {
            throw new RateLimitExceededException();
        }
        rateLimitPort.record(AttemptPurpose.SIGNUP, email.value(), metadata.ipAddress());

        String rawCode = generatePasscodePort.generate();
        SignupPasscode passcode =
                SignupPasscode.issue(email, rawCode, passwordHasher, now, authProperties.passcodeLifetime());

        if (loadUserPort.existsByEmail(email)) {
            appendAuthEventPort.append(AuthEvent.forSubject(AuthAction.DUPLICATE_SIGNUP_ATTEMPT, email, now, metadata));
            sendPasscodePort.sendDuplicateSignupNotice(email);
            return;
        }

        savePasscodePort.save(passcode);
        appendAuthEventPort.append(AuthEvent.forSubject(AuthAction.SIGNUP_PASSCODE_ISSUED, email, now, metadata));
        sendPasscodePort.sendPasscode(email, rawCode);
    }
}
