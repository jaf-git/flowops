package com.flowops.auth.application.reauthenticate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowops.auth.application.shared.AttemptPurpose;
import com.flowops.auth.application.shared.AuthProperties;
import com.flowops.auth.application.shared.ClientContext;
import com.flowops.auth.application.shared.SessionMetadataFactory;
import com.flowops.auth.application.shared.exception.AuthenticationRefusedException;
import com.flowops.auth.application.shared.exception.RateLimitExceededException;
import com.flowops.auth.application.shared.port.AppendAuthEventPort;
import com.flowops.auth.application.shared.port.LoadCredentialPort;
import com.flowops.auth.application.shared.port.RateLimitPort;
import com.flowops.auth.application.shared.port.ResolveCoarseLocationPort;
import com.flowops.auth.application.shared.port.SessionRegistryPort;
import com.flowops.auth.domain.enums.AuthAction;
import com.flowops.auth.domain.event.AuthEvent;
import com.flowops.auth.domain.model.Credential;
import com.flowops.auth.domain.model.SessionMetadata;
import com.flowops.auth.domain.model.UserId;
import com.flowops.auth.domain.service.ReversibleHasher;
import java.time.Clock;
import java.time.Duration;
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

@Tag("AUTH-REAUTH-01")
@ExtendWith(MockitoExtension.class)
class ReauthenticateServiceTest {
    private static final String ADDRESS = "203.0.113.10";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0) Chrome/120.0";
    private static final String PASSWORD = "correct horse battery";
    private static final Instant NOW = Instant.parse("2026-08-02T09:00:00Z");
    private static final Duration WINDOW = Duration.ofMinutes(15);

    @Mock
    private SessionRegistryPort sessionRegistryPort;

    @Mock
    private LoadCredentialPort loadCredentialPort;

    @Mock
    private AppendAuthEventPort appendAuthEventPort;

    @Mock
    private RateLimitPort rateLimitPort;

    @Mock
    private ResolveCoarseLocationPort resolveCoarseLocationPort;

    private ReversibleHasher hasher;
    private UserId userId;
    private ReauthenticateService service;

    @BeforeEach
    void setUp() {
        hasher = new ReversibleHasher();
        userId = UserId.generate();
        service = new ReauthenticateService(
                sessionRegistryPort,
                loadCredentialPort,
                appendAuthEventPort,
                rateLimitPort,
                new SessionMetadataFactory(resolveCoarseLocationPort),
                hasher,
                properties(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void aCorrectPasswordElevatesTheSessionForTheWindow() {
        givenAnActiveSessionHolding(PASSWORD);

        Instant until = service.execute(command(PASSWORD));

        assertThat(until).isEqualTo(NOW.plus(WINDOW));
        verify(sessionRegistryPort).markReauthenticated(NOW.plus(WINDOW));
    }

    @Test
    void aSuccessfulReauthenticationIsRecorded() {
        givenAnActiveSessionHolding(PASSWORD);

        service.execute(command(PASSWORD));

        assertThat(appendedEvent().action()).isEqualTo(AuthAction.REAUTHENTICATION_SUCCEEDED);
        assertThat(appendedEvent().actor()).contains(userId);
    }

    @Test
    void aWrongPasswordIsRefused() {
        givenAnActiveSessionHolding(PASSWORD);

        assertThatThrownBy(() -> service.execute(command("not the password")))
                .isInstanceOf(AuthenticationRefusedException.class);
    }

    @Test
    void aWrongPasswordLeavesTheSessionUnelevated() {
        givenAnActiveSessionHolding(PASSWORD);

        assertThatThrownBy(() -> service.execute(command("not the password")))
                .isInstanceOf(AuthenticationRefusedException.class);

        verify(sessionRegistryPort, never()).markReauthenticated(any());
    }

    @Test
    void aFailedReauthenticationIsRecorded() {
        givenAnActiveSessionHolding(PASSWORD);

        assertThatThrownBy(() -> service.execute(command("not the password")))
                .isInstanceOf(AuthenticationRefusedException.class);

        assertThat(appendedEvent().action()).isEqualTo(AuthAction.REAUTHENTICATION_FAILED);
        assertThat(appendedEvent().actor()).contains(userId);
    }

    @Test
    void aFailedAttemptIsCountedAgainstTheLimit() {
        givenAnActiveSessionHolding(PASSWORD);

        assertThatThrownBy(() -> service.execute(command("not the password")))
                .isInstanceOf(AuthenticationRefusedException.class);

        verify(rateLimitPort).record(eq(AttemptPurpose.REAUTHENTICATION), anyString(), eq(ADDRESS));
    }

    @Test
    void aSuccessIsNotCountedAgainstTheLimitBecauseACorrectPasswordIsNotAbuse() {
        givenAnActiveSessionHolding(PASSWORD);

        service.execute(command(PASSWORD));

        verify(rateLimitPort, never()).record(any(), anyString(), anyString());
    }

    @Test
    void aLimitedCallerIsRefusedBeforeAnyComparison() {
        when(sessionRegistryPort.currentUserId()).thenReturn(Optional.of(userId));
        when(resolveCoarseLocationPort.resolve(ADDRESS)).thenReturn(SessionMetadata.UNKNOWN);
        when(rateLimitPort.isLimited(eq(AttemptPurpose.REAUTHENTICATION), anyString(), eq(ADDRESS)))
                .thenReturn(true);

        assertThatThrownBy(() -> service.execute(command(PASSWORD))).isInstanceOf(RateLimitExceededException.class);

        assertThat(hasher.matchCalls()).isZero();
        verify(sessionRegistryPort, never()).markReauthenticated(any());
    }

    @Test
    void aCallerWithNoSessionIsRefused() {
        when(sessionRegistryPort.currentUserId()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(command(PASSWORD))).isInstanceOf(AuthenticationRefusedException.class);

        verify(sessionRegistryPort, never()).markReauthenticated(any());
    }

    @Test
    void anAccountWithNoCredentialIsRefusedAtTheSameCost() {
        when(sessionRegistryPort.currentUserId()).thenReturn(Optional.of(userId));
        when(resolveCoarseLocationPort.resolve(ADDRESS)).thenReturn(SessionMetadata.UNKNOWN);
        when(loadCredentialPort.loadFor(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(command(PASSWORD))).isInstanceOf(AuthenticationRefusedException.class);

        assertThat(hasher.matchCalls()).isEqualTo(1);
        verify(sessionRegistryPort, never()).markReauthenticated(any());
    }

    private void givenAnActiveSessionHolding(String password) {
        when(sessionRegistryPort.currentUserId()).thenReturn(Optional.of(userId));
        when(resolveCoarseLocationPort.resolve(ADDRESS)).thenReturn(SessionMetadata.UNKNOWN);
        when(loadCredentialPort.loadFor(userId))
                .thenReturn(Optional.of(Credential.issue(userId, password, hasher, NOW)));
        hasher.resetCounts();
    }

    private AuthEvent appendedEvent() {
        ArgumentCaptor<AuthEvent> appended = ArgumentCaptor.forClass(AuthEvent.class);
        verify(appendAuthEventPort).append(appended.capture());
        return appended.getValue();
    }

    private ReauthenticateCommand command(String password) {
        return new ReauthenticateCommand(password, new ClientContext(ADDRESS, USER_AGENT));
    }

    private AuthProperties properties() {
        return new AuthProperties(
                Duration.ofMinutes(10),
                5,
                WINDOW,
                Duration.ofMinutes(15),
                5,
                20,
                Duration.ofMinutes(60),
                Duration.ofHours(24),
                3,
                20,
                "http://localhost:5173/reset-password",
                "no-reply@flowops.local");
    }
}
