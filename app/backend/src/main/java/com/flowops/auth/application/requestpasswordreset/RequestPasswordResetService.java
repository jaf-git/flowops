package com.flowops.auth.application.requestpasswordreset;

import com.flowops.auth.application.shared.AttemptPurpose;
import com.flowops.auth.application.shared.AuthProperties;
import com.flowops.auth.application.shared.SessionMetadataFactory;
import com.flowops.auth.application.shared.port.AppendAuthEventPort;
import com.flowops.auth.application.shared.port.GenerateResetTokenPort;
import com.flowops.auth.application.shared.port.LoadUserPort;
import com.flowops.auth.application.shared.port.RateLimitPort;
import com.flowops.auth.application.shared.port.SaveResetTokenPort;
import com.flowops.auth.application.shared.port.SendResetLinkPort;
import com.flowops.auth.domain.enums.AuthAction;
import com.flowops.auth.domain.event.AuthEvent;
import com.flowops.auth.domain.model.EmailAddress;
import com.flowops.auth.domain.model.ResetToken;
import com.flowops.auth.domain.model.SessionMetadata;
import com.flowops.auth.domain.model.User;
import com.flowops.auth.domain.model.UserId;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestPasswordResetService implements RequestPasswordResetUseCase {
    private final LoadUserPort loadUserPort;
    private final SaveResetTokenPort saveResetTokenPort;
    private final GenerateResetTokenPort generateResetTokenPort;
    private final AppendAuthEventPort appendAuthEventPort;
    private final SendResetLinkPort sendResetLinkPort;
    private final RateLimitPort rateLimitPort;
    private final SessionMetadataFactory sessionMetadataFactory;
    private final AuthProperties authProperties;
    private final Clock clock;

    public RequestPasswordResetService(
            LoadUserPort loadUserPort,
            SaveResetTokenPort saveResetTokenPort,
            GenerateResetTokenPort generateResetTokenPort,
            AppendAuthEventPort appendAuthEventPort,
            SendResetLinkPort sendResetLinkPort,
            RateLimitPort rateLimitPort,
            SessionMetadataFactory sessionMetadataFactory,
            AuthProperties authProperties,
            Clock clock) {
        this.loadUserPort = loadUserPort;
        this.saveResetTokenPort = saveResetTokenPort;
        this.generateResetTokenPort = generateResetTokenPort;
        this.appendAuthEventPort = appendAuthEventPort;
        this.sendResetLinkPort = sendResetLinkPort;
        this.rateLimitPort = rateLimitPort;
        this.sessionMetadataFactory = sessionMetadataFactory;
        this.authProperties = authProperties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void execute(RequestPasswordResetCommand command) {
        EmailAddress email = new EmailAddress(command.email());
        SessionMetadata metadata = sessionMetadataFactory.from(command.clientContext());
        Instant now = clock.instant();

        if (rateLimitPort.isLimited(AttemptPurpose.PASSWORD_RESET, email.value(), metadata.ipAddress())) {
            return;
        }
        rateLimitPort.record(AttemptPurpose.PASSWORD_RESET, email.value(), metadata.ipAddress());

        Optional<User> user = loadUserPort.loadByEmail(email);

        GenerateResetTokenPort.MintedResetToken minted = generateResetTokenPort.mint();
        ResetToken token = ResetToken.issue(
                user.map(User::id).orElseGet(UserId::generate),
                minted.tokenHash(),
                now,
                authProperties.resetTokenLifetime());

        appendAuthEventPort.append(AuthEvent.forSubject(AuthAction.PASSWORD_RESET_REQUESTED, email, now, metadata));

        if (user.isEmpty() || !user.get().canAuthenticate()) {
            return;
        }

        saveResetTokenPort.save(token);
        sendResetLinkPort.sendResetLink(email, minted.clearToken());
    }
}
