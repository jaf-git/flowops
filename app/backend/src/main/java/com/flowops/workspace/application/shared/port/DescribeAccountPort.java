package com.flowops.workspace.application.shared.port;

import com.flowops.workspace.domain.model.PersonId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DescribeAccountPort {
    Optional<Account> describe(PersonId person);

    record Account(
            PersonId person,
            String emailAddress,
            Optional<String> displayName,
            String role,
            String accountState,
            Instant createdAt,
            List<Session> sessions) {}

    record Session(
            UUID reference, Optional<String> deviceSummary, Optional<String> coarseLocation, Instant createdAt) {}
}
