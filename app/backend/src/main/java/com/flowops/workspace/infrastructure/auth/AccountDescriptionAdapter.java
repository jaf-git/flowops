package com.flowops.workspace.infrastructure.auth;

import com.flowops.auth.application.describeaccount.DescribeAccountUseCase;
import com.flowops.workspace.application.shared.port.DescribeAccountPort;
import com.flowops.workspace.domain.model.PersonId;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class AccountDescriptionAdapter implements DescribeAccountPort {
    private final DescribeAccountUseCase describeAccount;

    public AccountDescriptionAdapter(DescribeAccountUseCase describeAccount) {
        this.describeAccount = describeAccount;
    }

    @Override
    public Optional<Account> describe(PersonId person) {
        return describeAccount
                .execute(person.value())
                .map(described -> new Account(
                        PersonId.of(described.personId()),
                        described.emailAddress(),
                        described.displayName(),
                        described.role(),
                        described.accountState(),
                        described.createdAt(),
                        described.sessions().stream()
                                .map(session -> new Session(
                                        session.reference(),
                                        session.deviceSummary(),
                                        session.coarseLocation(),
                                        session.createdAt()))
                                .toList()));
    }
}
