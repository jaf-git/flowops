package com.flowops.auth.application.setdisplayname;

import com.flowops.auth.application.shared.exception.AuthenticationRefusedException;
import com.flowops.auth.application.shared.port.LoadUserPort;
import com.flowops.auth.application.shared.port.SaveUserPort;
import com.flowops.auth.application.shared.port.SessionRegistryPort;
import com.flowops.auth.domain.model.DisplayName;
import com.flowops.auth.domain.model.User;
import com.flowops.auth.domain.model.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SetDisplayNameService implements SetDisplayNameUseCase {
    private final SessionRegistryPort sessionRegistryPort;
    private final LoadUserPort loadUserPort;
    private final SaveUserPort saveUserPort;

    public SetDisplayNameService(
            SessionRegistryPort sessionRegistryPort, LoadUserPort loadUserPort, SaveUserPort saveUserPort) {
        this.sessionRegistryPort = sessionRegistryPort;
        this.loadUserPort = loadUserPort;
        this.saveUserPort = saveUserPort;
    }

    @Override
    @Transactional
    public void execute(SetDisplayNameCommand command) {
        UserId userId = sessionRegistryPort.currentUserId().orElseThrow(AuthenticationRefusedException::new);
        User user = loadUserPort.loadById(userId).orElseThrow(AuthenticationRefusedException::new);

        saveUserPort.save(user.withDisplayName(new DisplayName(command.displayName())));
    }
}
