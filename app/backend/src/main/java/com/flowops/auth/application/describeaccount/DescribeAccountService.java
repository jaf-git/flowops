package com.flowops.auth.application.describeaccount;

import com.flowops.auth.application.shared.port.LoadUserPort;
import com.flowops.auth.application.shared.port.SessionRegistryPort;
import com.flowops.auth.domain.model.ActiveSession;
import com.flowops.auth.domain.model.SessionMetadata;
import com.flowops.auth.domain.model.User;
import com.flowops.auth.domain.model.UserId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DescribeAccountService implements DescribeAccountUseCase {
    private final LoadUserPort loadUserPort;
    private final SessionRegistryPort sessionRegistryPort;

    public DescribeAccountService(LoadUserPort loadUserPort, SessionRegistryPort sessionRegistryPort) {
        this.loadUserPort = loadUserPort;
        this.sessionRegistryPort = sessionRegistryPort;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccountDescription> execute(UUID personId) {
        UserId id = UserId.of(personId);
        return loadUserPort.loadById(id).map(user -> describe(user, id));
    }

    private AccountDescription describe(User user, UserId id) {
        return new AccountDescription(
                user.id().value(),
                user.email().value(),
                user.displayName().map(name -> name.value()),
                user.role().value(),
                user.accountState().name(),
                user.createdAt(),
                sessionRegistryPort.sessionsOf(id).stream().map(this::describe).toList());
    }

    private SessionDescription describe(ActiveSession session) {
        return new SessionDescription(
                session.reference(),
                stated(session.metadata().deviceSummary()),
                stated(session.metadata().coarseLocation()),
                session.createdAt());
    }

    private Optional<String> stated(String value) {
        return SessionMetadata.UNKNOWN.equals(value) ? Optional.empty() : Optional.of(value);
    }
}
