package com.flowops.auth.application.shared;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.flowops.auth.application.shared.exception.ReauthenticationRequiredException;
import com.flowops.auth.application.shared.port.SessionRegistryPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("AUTH-REAUTH-01")
@ExtendWith(MockitoExtension.class)
class SensitiveActionGuardTest {
    private static final Instant NOW = Instant.parse("2026-08-02T09:00:00Z");

    @Mock
    private SessionRegistryPort sessionRegistryPort;

    private SensitiveActionGuard guard;

    @BeforeEach
    void setUp() {
        guard = new SensitiveActionGuard(sessionRegistryPort, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void aSessionInsideItsWindowMayPerformTheAction() {
        when(sessionRegistryPort.reauthenticatedUntil()).thenReturn(Optional.of(NOW.plusSeconds(60)));

        assertThatCode(() -> guard.requireRecentReauthentication()).doesNotThrowAnyException();
    }

    @Test
    void aSessionThatNeverReauthenticatedIsChallenged() {
        when(sessionRegistryPort.reauthenticatedUntil()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.requireRecentReauthentication())
                .isInstanceOf(ReauthenticationRequiredException.class);
    }

    @Test
    void anExpiredWindowIsChallengedAgain() {
        when(sessionRegistryPort.reauthenticatedUntil()).thenReturn(Optional.of(NOW.minusSeconds(1)));

        assertThatThrownBy(() -> guard.requireRecentReauthentication())
                .isInstanceOf(ReauthenticationRequiredException.class);
    }

    @Test
    void theWindowIsClosedAtTheInstantItExpires() {
        when(sessionRegistryPort.reauthenticatedUntil()).thenReturn(Optional.of(NOW));

        assertThatThrownBy(() -> guard.requireRecentReauthentication())
                .isInstanceOf(ReauthenticationRequiredException.class);
    }
}
