package com.flowops.auth.application.completesetup;

import com.flowops.auth.application.shared.SessionMetadataFactory;
import com.flowops.auth.application.shared.exception.AuthenticationRefusedException;
import com.flowops.auth.application.shared.port.AppendAuthEventPort;
import com.flowops.auth.application.shared.port.LoadUserPort;
import com.flowops.auth.application.shared.port.SaveUserPort;
import com.flowops.auth.application.shared.port.SessionRegistryPort;
import com.flowops.auth.domain.enums.AuthAction;
import com.flowops.auth.domain.event.AuthEvent;
import com.flowops.auth.domain.model.User;
import com.flowops.auth.domain.model.UserId;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompleteSetupService implements CompleteSetupUseCase {
    private final SessionRegistryPort sessionRegistryPort;
    private final LoadUserPort loadUserPort;
    private final SaveUserPort saveUserPort;
    private final AppendAuthEventPort appendAuthEventPort;
    private final SessionMetadataFactory sessionMetadataFactory;
    private final Clock clock;

    public CompleteSetupService(
            SessionRegistryPort sessionRegistryPort,
            LoadUserPort loadUserPort,
            SaveUserPort saveUserPort,
            AppendAuthEventPort appendAuthEventPort,
            SessionMetadataFactory sessionMetadataFactory,
            Clock clock) {
        this.sessionRegistryPort = sessionRegistryPort;
        this.loadUserPort = loadUserPort;
        this.saveUserPort = saveUserPort;
        this.appendAuthEventPort = appendAuthEventPort;
        this.sessionMetadataFactory = sessionMetadataFactory;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void execute(CompleteSetupCommand command) {
        UserId userId = sessionRegistryPort.currentUserId().orElseThrow(AuthenticationRefusedException::new);
        User user = loadUserPort.loadById(userId).orElseThrow(AuthenticationRefusedException::new);

        if (user.setupCompleted()) {
            return;
        }

        saveUserPort.save(user.completeSetup());
        appendAuthEventPort.append(AuthEvent.byActor(
                AuthAction.SETUP_COMPLETED,
                userId,
                clock.instant(),
                sessionMetadataFactory.from(command.clientContext())));
    }
}
