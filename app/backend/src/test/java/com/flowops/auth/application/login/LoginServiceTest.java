package com.flowops.auth.application.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.flowops.auth.application.shared.AttemptPurpose;
import com.flowops.auth.application.shared.ClientContext;
import com.flowops.auth.application.shared.SessionContext;
import com.flowops.auth.application.shared.SessionMetadataFactory;
import com.flowops.auth.application.shared.exception.AuthenticationRefusedException;
import com.flowops.auth.application.shared.exception.RateLimitExceededException;
import com.flowops.auth.application.shared.port.AppendAuthEventPort;
import com.flowops.auth.application.shared.port.LoadCredentialPort;
import com.flowops.auth.application.shared.port.LoadUserPort;
import com.flowops.auth.application.shared.port.RateLimitPort;
import com.flowops.auth.application.shared.port.ResolveCoarseLocationPort;
import com.flowops.auth.application.shared.port.ResolvePermissionsPort;
import com.flowops.auth.application.shared.port.SessionRegistryPort;
import com.flowops.auth.domain.enums.AccountState;
import com.flowops.auth.domain.enums.AuthAction;
import com.flowops.auth.domain.enums.LandingTarget;
import com.flowops.auth.domain.event.AuthEvent;
import com.flowops.auth.domain.model.AuthPermissions;
import com.flowops.auth.domain.model.Credential;
import com.flowops.auth.domain.model.EmailAddress;
import com.flowops.auth.domain.model.RoleName;
import com.flowops.auth.domain.model.SessionMetadata;
import com.flowops.auth.domain.model.User;
import com.flowops.auth.domain.model.UserId;
import com.flowops.auth.domain.service.ReversibleHasher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("AUTH-LOGIN-01")
@ExtendWith(MockitoExtension.class)
class LoginServiceTest {
    private static final String RAW_EMAIL = "founder@flowops.test";
    private static final EmailAddress EMAIL = new EmailAddress(RAW_EMAIL);
    private static final String ADDRESS = "203.0.113.10";
    private static final String PASSWORD = "correcthorsebattery";
    private static final Instant NOW = Instant.parse("2026-08-02T09:00:00Z");

    private final ReversibleHasher hasher = new ReversibleHasher();

    @Mock
    private LoadUserPort loadUserPort;

    @Mock
    private LoadCredentialPort loadCredentialPort;

    @Mock
    private SessionRegistryPort sessionRegistryPort;

    @Mock
    private ResolvePermissionsPort resolvePermissionsPort;

    @Mock
    private AppendAuthEventPort appendAuthEventPort;

    @Mock
    private RateLimitPort rateLimitPort;

    @Mock
    private ResolveCoarseLocationPort resolveCoarseLocationPort;

    private LoginService service;

    @BeforeEach
    void setUp() {
        service = new LoginService(
                loadUserPort,
                loadCredentialPort,
                sessionRegistryPort,
                resolvePermissionsPort,
                appendAuthEventPort,
                rateLimitPort,
                new SessionMetadataFactory(resolveCoarseLocationPort),
                hasher,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(resolveCoarseLocationPort.resolve(ADDRESS)).thenReturn(SessionMetadata.UNKNOWN);
    }

    @Test
    void anActiveAccountWithTheCorrectPasswordOpensASession() {
        User user = activeUser();
        givenAccount(user);
        when(resolvePermissionsPort.resolveFor(user.id())).thenReturn(Set.of(AuthPermissions.SESSION_VIEW_OWN));

        SessionContext context = service.execute(command(PASSWORD));

        verify(sessionRegistryPort)
                .open(user, new SessionMetadata(ADDRESS, "Chrome on Windows", SessionMetadata.UNKNOWN));
        assertThat(context.permissions()).containsExactly(AuthPermissions.SESSION_VIEW_OWN);
    }

    @Test
    void aSuccessfulLoginIsRecorded() {
        User user = activeUser();
        givenAccount(user);
        when(resolvePermissionsPort.resolveFor(user.id())).thenReturn(Set.of());

        service.execute(command(PASSWORD));

        ArgumentCaptor<AuthEvent> appended = ArgumentCaptor.forClass(AuthEvent.class);
        verify(appendAuthEventPort).append(appended.capture());
        assertThat(appended.getValue().action()).isEqualTo(AuthAction.LOGIN_SUCCEEDED);
    }

    @Test
    void theOwnerLandsOnTheSupervisionScreen() {
        User user = activeUser();
        givenAccount(user);
        when(resolvePermissionsPort.resolveFor(user.id())).thenReturn(Set.of());

        SessionContext context = service.execute(command(PASSWORD));

        assertThat(context.landingTarget()).isEqualTo(LandingTarget.TRIAGE);
    }

    @Test
    void aWrongPasswordDoesNotAuthenticate() {
        givenAccount(activeUser());

        assertThatThrownBy(() -> service.execute(command("wrong-password-entirely")))
                .isInstanceOf(AuthenticationRefusedException.class);

        verify(sessionRegistryPort, never()).open(any(), any());
    }

    @Test
    void aWrongPasswordIsRecordedAsAFailedLogin() {
        givenAccount(activeUser());

        assertThatThrownBy(() -> service.execute(command("wrong-password-entirely")))
                .isInstanceOf(AuthenticationRefusedException.class);

        ArgumentCaptor<AuthEvent> appended = ArgumentCaptor.forClass(AuthEvent.class);
        verify(appendAuthEventPort).append(appended.capture());
        assertThat(appended.getValue().action()).isEqualTo(AuthAction.LOGIN_FAILED);
    }

    @Test
    void anUnknownAddressIsRefusedWithTheSameExceptionAsAWrongPassword() {
        when(loadUserPort.loadByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(command(PASSWORD))).isInstanceOf(AuthenticationRefusedException.class);
    }

    @Test
    void aDeactivatedAccountIsRefusedEvenWithTheCorrectPassword() {
        User deactivated =
                User.rebuild(UserId.generate(), EMAIL, AccountState.DEACTIVATED, RoleName.EMPLOYEE, NOW, true, null);
        givenAccount(deactivated);

        assertThatThrownBy(() -> service.execute(command(PASSWORD))).isInstanceOf(AuthenticationRefusedException.class);

        verify(sessionRegistryPort, never()).open(any(), any());
    }

    @Test
    void aFailedAttemptIsCountedTowardTheRateLimit() {
        givenAccount(activeUser());

        assertThatThrownBy(() -> service.execute(command("wrong-password-entirely")))
                .isInstanceOf(AuthenticationRefusedException.class);

        verify(rateLimitPort).record(AttemptPurpose.LOGIN, RAW_EMAIL, ADDRESS);
    }

    @Test
    void anUnknownAddressCostsTheSameComparisonAsAWrongPasswordOnAKnownOne() {
        givenAccount(activeUser());
        assertThatThrownBy(() -> service.execute(command("wrong-password-entirely")))
                .isInstanceOf(AuthenticationRefusedException.class);
        int onAKnownAddress = hasher.matchCalls();

        hasher.resetCounts();
        when(loadUserPort.loadByEmail(EMAIL)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.execute(command(PASSWORD))).isInstanceOf(AuthenticationRefusedException.class);

        assertThat(hasher.matchCalls()).isEqualTo(onAKnownAddress).isPositive();
    }

    @Test
    void bothRefusalsReadTheSameRowsAndWriteTheSameRows() {
        givenAccount(activeUser());
        assertThatThrownBy(() -> service.execute(command("wrong-password-entirely")))
                .isInstanceOf(AuthenticationRefusedException.class);
        assertEveryPortWasTouchedExactlyOnce();

        reset(loadCredentialPort, appendAuthEventPort, rateLimitPort);
        when(loadUserPort.loadByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(command(PASSWORD))).isInstanceOf(AuthenticationRefusedException.class);

        assertEveryPortWasTouchedExactlyOnce();
    }

    private void assertEveryPortWasTouchedExactlyOnce() {
        verify(rateLimitPort).isLimited(AttemptPurpose.LOGIN, RAW_EMAIL, ADDRESS);
        verify(loadCredentialPort).loadFor(any());
        verify(appendAuthEventPort).append(any(AuthEvent.class));
        verify(rateLimitPort).record(AttemptPurpose.LOGIN, RAW_EMAIL, ADDRESS);
        verifyNoMoreInteractions(loadCredentialPort, appendAuthEventPort, rateLimitPort);
    }

    @Test
    void anUnknownAddressIsRecordedAsAFailedLoginJustAsAWrongPasswordIs() {
        when(loadUserPort.loadByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(command(PASSWORD))).isInstanceOf(AuthenticationRefusedException.class);

        ArgumentCaptor<AuthEvent> appended = ArgumentCaptor.forClass(AuthEvent.class);
        verify(appendAuthEventPort).append(appended.capture());
        assertThat(appended.getValue().action()).isEqualTo(AuthAction.LOGIN_FAILED);
    }

    @Test
    void attemptsBeyondTheLimitAreRefusedBeforeTheCredentialIsEvenRead() {
        when(rateLimitPort.isLimited(AttemptPurpose.LOGIN, RAW_EMAIL, ADDRESS)).thenReturn(true);

        assertThatThrownBy(() -> service.execute(command(PASSWORD))).isInstanceOf(RateLimitExceededException.class);

        verify(loadCredentialPort, never()).loadFor(any());
    }

    private User activeUser() {
        return User.rebuild(UserId.generate(), EMAIL, AccountState.ACTIVE, RoleName.OWNER, NOW, true, null);
    }

    private void givenAccount(User user) {
        when(loadUserPort.loadByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(loadCredentialPort.loadFor(user.id()))
                .thenReturn(Optional.of(Credential.issue(user.id(), PASSWORD, hasher, NOW)));
    }

    private LoginCommand command(String password) {
        return new LoginCommand(
                RAW_EMAIL, password, new ClientContext(ADDRESS, "Mozilla/5.0 (Windows NT 10.0) Chrome/120.0"));
    }
}
