package com.flowops.auth.application.viewownsessions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowops.auth.application.shared.exception.AuthenticationRefusedException;
import com.flowops.auth.application.shared.port.SessionRegistryPort;
import com.flowops.auth.domain.model.ActiveSession;
import com.flowops.auth.domain.model.SessionMetadata;
import com.flowops.auth.domain.model.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("AUTH-VIEW-SESSIONS-01")
@ExtendWith(MockitoExtension.class)
class ViewOwnSessionsServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-02T09:00:00Z");

    @Mock
    private SessionRegistryPort sessionRegistryPort;

    private UserId caller;
    private ViewOwnSessionsService service;

    @BeforeEach
    void setUp() {
        caller = UserId.generate();
        service = new ViewOwnSessionsService(sessionRegistryPort);
    }

    @Test
    void theSubjectIsTheCallerAndNobodyTheCallerCouldName() {
        when(sessionRegistryPort.currentUserId()).thenReturn(Optional.of(caller));
        when(sessionRegistryPort.sessionsOf(caller)).thenReturn(List.of(session(true)));

        service.execute();

        verify(sessionRegistryPort).sessionsOf(caller);
    }

    @Test
    void theCallersSessionsAreReturnedAsTheyCome() {
        ActiveSession current = session(true);
        ActiveSession other = session(false);
        when(sessionRegistryPort.currentUserId()).thenReturn(Optional.of(caller));
        when(sessionRegistryPort.sessionsOf(caller)).thenReturn(List.of(current, other));

        assertThat(service.execute()).containsExactly(current, other);
    }

    @Test
    void aPersonSignedInOnceSeesExactlyThatSessionMarkedCurrent() {
        ActiveSession only = session(true);
        when(sessionRegistryPort.currentUserId()).thenReturn(Optional.of(caller));
        when(sessionRegistryPort.sessionsOf(caller)).thenReturn(List.of(only));

        List<ActiveSession> listed = service.execute();

        assertThat(listed).singleElement().satisfies(entry -> assertThat(entry.current())
                .isTrue());
    }

    @Test
    void aCallerWithNoLiveSessionIsRefusedAndNothingIsRead() {
        when(sessionRegistryPort.currentUserId()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute()).isInstanceOf(AuthenticationRefusedException.class);

        verify(sessionRegistryPort, never()).sessionsOf(any());
    }

    private ActiveSession session(boolean current) {
        return new ActiveSession(
                UUID.randomUUID(),
                NOW,
                NOW,
                new SessionMetadata("203.0.113.10", "Chrome on Windows", SessionMetadata.UNKNOWN),
                current);
    }
}
