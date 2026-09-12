package com.flowops.auth.application.findpersonbyemail;

import com.flowops.auth.application.shared.port.LoadUserPort;
import com.flowops.auth.domain.model.EmailAddress;
import com.flowops.auth.domain.model.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FindPersonByEmailService implements FindPersonByEmailUseCase {
    private final LoadUserPort loadUserPort;

    public FindPersonByEmailService(LoadUserPort loadUserPort) {
        this.loadUserPort = loadUserPort;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> execute(String email) {
        return loadUserPort.loadByEmail(new EmailAddress(email)).map(User::id).map(id -> id.value());
    }
}
