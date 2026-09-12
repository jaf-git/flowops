package com.flowops.auth.application.shared.port;

import com.flowops.auth.domain.model.ActiveSession;
import com.flowops.auth.domain.model.SessionMetadata;
import com.flowops.auth.domain.model.User;
import com.flowops.auth.domain.model.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionRegistryPort {
    void open(User user, SessionMetadata metadata);

    void endCurrent();

    Optional<UserId> currentUserId();

    void markReauthenticated(Instant until);

    Optional<Instant> reauthenticatedUntil();

    void endEverySessionFor(UserId userId);

    List<ActiveSession> sessionsOf(UserId userId);

    Optional<UserId> endByReference(UUID reference);

    Optional<UserId> ownerOf(UUID reference);
}
