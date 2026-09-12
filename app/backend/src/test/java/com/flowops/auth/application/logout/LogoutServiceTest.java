package com.flowops.auth.application.logout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowops.auth.application.shared.ClientContext;
import com.flowops.auth.application.shared.SessionMetadataFactory;
import com.flowops.auth.application.shared.port.AppendAuthEventPort;
import com.flowops.auth.application.shared.port.ResolveCoarseLocationPort;
import com.flowops.auth.application.shared.port.SessionRegistryPort;
import com.flowops.auth.domain.enums.AuthAction;
import com.flowops.auth.domain.event.AuthEvent;
import com.flowops.auth.domain.model.SessionMetadata;
import com.flowops.auth.domain.model.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("AUTH-LOGOUT-01")
@ExtendWith(MockitoExtension.class)
class LogoutServiceTest {
    private static final String ADDRESS = "203.0.113.10";
    private static final Instant NOW = Instant.parse("2026-08-02T09:00:00Z");

    @Mock
    private SessionRegistryPort sessionRegistryPort;

    @Mock
    private AppendAuthEventPort appendAuthEventPort;

    @Mock
    private ResolveCoarseLocationPort resolveCoarseLocationPort;

    private LogoutService service;

    @BeforeEach
    void setUp() {
        service = new LogoutService(
                sessionRegistryPort,
                appendAuthEventPort,
                new SessionMetadataFactory(resolveCoarseLocationPort),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void anActiveSessionIsEnded() {
        when(sessionRegistryPort.currentUserId()).thenReturn(Optional.of(UserId.generate()));
        when(resolveCoarseLocationPort.resolve(ADDRESS)).thenReturn(SessionMetadata.UNKNOWN);

        service.execute(command());

        verify(sessionRegistryPort).endCurrent();
    }

    @Test
    void endingASessionIsRecorded() {
        UserId userId = UserId.generate();
        when(sessionRegistryPort.currentUserId()).thenReturn(Optional.of(userId));
        when(resolveCoarseLocationPort.resolve(ADDRESS)).thenReturn(SessionMetadata.UNKNOWN);

        service.execute(command());

        ArgumentCaptor<AuthEvent> appended = ArgumentCaptor.forClass(AuthEvent.class);
        verify(appendAuthEventPort).append(appended.capture());
        assertThat(appended.getValue().action()).isEqualTo(AuthAction.LOGGED_OUT);
        assertThat(appended.getValue().actor()).contains(userId);
    }

    @Test
    void theEventIsAppendedBeforeTheSessionEndsSoTheActorIsStillKnown() {
        when(sessionRegistryPort.currentUserId()).thenReturn(Optional.of(UserId.generate()));
        when(resolveCoarseLocationPort.resolve(ADDRESS)).thenReturn(SessionMetadata.UNKNOWN);

        service.execute(command());

        var order = org.mockito.Mockito.inOrder(appendAuthEventPort, sessionRegistryPort);
        order.verify(appendAuthEventPort).append(any(AuthEvent.class));
        order.verify(sessionRegistryPort).endCurrent();
    }

    @Test
    void anAlreadyExpiredSessionLogsOutWithoutAnError() {
        when(sessionRegistryPort.currentUserId()).thenReturn(Optional.empty());

        assertThatCode(() -> service.execute(command())).doesNotThrowAnyException();
    }

    @Test
    void anAlreadyExpiredSessionRecordsNothingBecauseNothingHappened() {
        when(sessionRegistryPort.currentUserId()).thenReturn(Optional.empty());

        service.execute(command());

        verify(appendAuthEventPort, never()).append(any());
        verify(sessionRegistryPort, never()).endCurrent();
    }

    private LogoutCommand command() {
        return new LogoutCommand(new ClientContext(ADDRESS, "Mozilla/5.0 (Windows NT 10.0) Chrome/120.0"));
    }
}
