package com.flowops.auth.application.describeaccount;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DescribeAccountUseCase {
    Optional<AccountDescription> execute(UUID personId);

    record AccountDescription(
            UUID personId,
            String emailAddress,
            Optional<String> displayName,
            String role,
            String accountState,
            Instant createdAt,
            List<SessionDescription> sessions) {}

    record SessionDescription(
            UUID reference, Optional<String> deviceSummary, Optional<String> coarseLocation, Instant createdAt) {}
}
