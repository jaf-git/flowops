package com.flowops.auth.application.shared;

import com.flowops.auth.application.shared.exception.ReauthenticationRequiredException;
import com.flowops.auth.application.shared.port.SessionRegistryPort;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class SensitiveActionGuard {
    private final SessionRegistryPort sessionRegistryPort;
    private final Clock clock;

    public SensitiveActionGuard(SessionRegistryPort sessionRegistryPort, Clock clock) {
        this.sessionRegistryPort = sessionRegistryPort;
        this.clock = clock;
    }

    public void requireRecentReauthentication() {
        Optional<Instant> until = sessionRegistryPort.reauthenticatedUntil();
        if (until.isEmpty() || !until.get().isAfter(clock.instant())) {
            throw new ReauthenticationRequiredException();
        }
    }
}
