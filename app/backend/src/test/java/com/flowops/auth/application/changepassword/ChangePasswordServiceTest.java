package com.flowops.auth.application.changepassword;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowops.auth.application.shared.ClientContext;
import com.flowops.auth.application.shared.SensitiveActionGuard;
import com.flowops.auth.application.shared.SessionMetadataFactory;
import com.flowops.auth.application.shared.exception.AuthenticationRefusedException;
import com.flowops.auth.application.shared.exception.ReauthenticationRequiredException;
import com.flowops.auth.application.shared.port.AppendAuthEventPort;
import com.flowops.auth.application.shared.port.LoadCredentialPort;
import com.flowops.auth.application.shared.port.LoadUserPort;
import com.flowops.auth.application.shared.port.ResolveCoarseLocationPort;
import com.flowops.auth.application.shared.port.SaveCredentialPort;
import com.flowops.auth.application.shared.port.SessionRegistryPort;
import com.flowops.auth.domain.enums.AuthAction;
import com.flowops.auth.domain.enums.PasswordRule;
import com.flowops.auth.domain.event.AuthEvent;
import com.flowops.auth.domain.exception.PasswordPolicyViolationException;
import com.flowops.auth.domain.model.Credential;
import com.flowops.auth.domain.model.EmailAddress;
import com.flowops.auth.domain.model.SessionMetadata;
import com.flowops.auth.domain.model.User;
import com.flowops.auth.domain.model.UserId;
import com.flowops.auth.domain.service.PasswordPolicy;
import com.flowops.auth.domain.service.ReversibleHasher;
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
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("AUTH-CHANGE-PASSWORD-01")
@ExtendWith(MockitoExtension.class)
class ChangePasswordServiceTest {
    private static final String ADDRESS = "203.0.113.10";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0) Chrome/120.0";
    private static final String CURRENT_PASSWORD = "the current password";
    private static final String NEW_PASSWORD = "a sufficiently long replacement";
    private static final Instant NOW = Instant.parse("2026-08-02T09:00:00Z");

    @Mock
    private SessionRegistryPort sessionRegistryPort;

    @Mock
    private LoadUserPort loadUserPort;

    @Mock
    private LoadCredentialPort loadCredentialPort;

    @Mock
    private SaveCredentialPort saveCredentialPort;

    @Mock
    private AppendAuthEventPort appendAuthEventPort;

    @Mock
    private SensitiveActionGuard sensitiveActionGuard;

    @Mock
    private ResolveCoarseLocationPort resolveCoarseLocationPort;

    private ReversibleHasher hasher;
    private UserId userId;
    private ChangePasswordService service;

    @BeforeEach
    void setUp() {
        hasher = new ReversibleHasher();
        userId = UserId.generate();
        service = new ChangePasswordService(
                sessionRegistryPort,
                loadUserPort,
                loadCredentialPort,
                saveCredentialPort,
                appendAuthEventPort,
                sensitiveActionGuard,
                new SessionMetadataFactory(resolveCoarseLocationPort),
                new PasswordPolicy(),
                hasher,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void theNewPasswordReplacesTheCredential() {
        givenAReauthenticatedSession();
        andTheLocationResolves();

        service.execute(command(NEW_PASSWORD));

        ArgumentCaptor<Credential> saved = ArgumentCaptor.forClass(Credential.class);
        verify(saveCredentialPort).save(saved.capture());
        assertThat(saved.getValue().userId()).isEqualTo(userId);
        assertThat(saved.getValue().matches(NEW_PASSWORD, hasher)).isTrue();
        assertThat(saved.getValue().matches(CURRENT_PASSWORD, hasher)).isFalse();
    }

    @Test
    void theChangeIsRecorded() {
        givenAReauthenticatedSession();
        andTheLocationResolves();

        service.execute(command(NEW_PASSWORD));

        assertThat(appendedEvent().action()).isEqualTo(AuthAction.PASSWORD_CHANGED);
        assertThat(appendedEvent().actor()).contains(userId);
    }

    @Test
    void everySessionIsEndedIncludingTheCallersOwn() {
        givenAReauthenticatedSession();
        andTheLocationResolves();

        service.execute(command(NEW_PASSWORD));

        verify(sessionRegistryPort).endEverySessionFor(userId);
    }

    @Test
    void aFreshSessionIsOpenedAfterEverySessionIsEnded() {
        givenAReauthenticatedSession();
        andTheLocationResolves();

        service.execute(command(NEW_PASSWORD));

        var order = Mockito.inOrder(sessionRegistryPort);
        order.verify(sessionRegistryPort).endEverySessionFor(userId);
        order.verify(sessionRegistryPort).open(any(User.class), any(SessionMetadata.class));
    }

    @Test
    void aRefusedChangeRotatesNothing() {
        givenAReauthenticatedSession();

        assertThatThrownBy(() -> service.execute(command("short")))
                .isInstanceOf(PasswordPolicyViolationException.class);

        verify(sessionRegistryPort, never()).endEverySessionFor(any());
        verify(sessionRegistryPort, never()).open(any(), any());
    }

    @Test
    void theCredentialIsReplacedBeforeTheOtherSessionsEnd() {
        givenAReauthenticatedSession();
        andTheLocationResolves();

        service.execute(command(NEW_PASSWORD));

        var order = Mockito.inOrder(saveCredentialPort, sessionRegistryPort, appendAuthEventPort);
        order.verify(saveCredentialPort).save(any(Credential.class));
        order.verify(sessionRegistryPort).endEverySessionFor(userId);
        order.verify(appendAuthEventPort).append(any(AuthEvent.class));
    }

    @Test
    void aPasswordFailingThePolicyIsRefusedNamingTheRule() {
        givenAReauthenticatedSession();

        assertThatThrownBy(() -> service.execute(command("short")))
                .isInstanceOf(PasswordPolicyViolationException.class)
                .extracting(failure -> ((PasswordPolicyViolationException) failure).violations())
                .isEqualTo(java.util.List.of(PasswordRule.MINIMUM_LENGTH));
    }

    @Test
    void aPasswordFailingThePolicyChangesNothing() {
        givenAReauthenticatedSession();

        assertThatThrownBy(() -> service.execute(command("short")))
                .isInstanceOf(PasswordPolicyViolationException.class);

        verify(saveCredentialPort, never()).save(any());
        verify(sessionRegistryPort, never()).endEverySessionFor(any());
        verify(appendAuthEventPort, never()).append(any());
    }

    @Test
    void theCurrentPasswordIsRefusedAsUnchanged() {
        givenAReauthenticatedSession();

        assertThatThrownBy(() -> service.execute(command(CURRENT_PASSWORD)))
                .isInstanceOf(PasswordPolicyViolationException.class)
                .extracting(failure -> ((PasswordPolicyViolationException) failure).violations())
                .isEqualTo(java.util.List.of(PasswordRule.NOT_CURRENT_PASSWORD));
    }

    @Test
    void anUnchangedPasswordChangesNothing() {
        givenAReauthenticatedSession();

        assertThatThrownBy(() -> service.execute(command(CURRENT_PASSWORD)))
                .isInstanceOf(PasswordPolicyViolationException.class);

        verify(saveCredentialPort, never()).save(any());
        verify(sessionRegistryPort, never()).endEverySessionFor(any());
    }

    @Test
    void aSessionOutsideItsWindowIsChallengedFirst() {
        when(sessionRegistryPort.currentUserId()).thenReturn(Optional.of(userId));
        doThrow(new ReauthenticationRequiredException())
                .when(sensitiveActionGuard)
                .requireRecentReauthentication();

        assertThatThrownBy(() -> service.execute(command(NEW_PASSWORD)))
                .isInstanceOf(ReauthenticationRequiredException.class);

        verify(saveCredentialPort, never()).save(any());
        verify(sessionRegistryPort, never()).endEverySessionFor(any());
        verify(appendAuthEventPort, never()).append(any());
    }

    @Test
    void theChallengeComesBeforeThePolicyIsApplied() {
        when(sessionRegistryPort.currentUserId()).thenReturn(Optional.of(userId));
        doThrow(new ReauthenticationRequiredException())
                .when(sensitiveActionGuard)
                .requireRecentReauthentication();

        assertThatThrownBy(() -> service.execute(command("short")))
                .isInstanceOf(ReauthenticationRequiredException.class);
    }

    @Test
    void aCallerWithNoSessionIsRefused() {
        when(sessionRegistryPort.currentUserId()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(command(NEW_PASSWORD)))
                .isInstanceOf(AuthenticationRefusedException.class);

        verify(saveCredentialPort, never()).save(any());
    }

    private void givenAReauthenticatedSession() {
        when(sessionRegistryPort.currentUserId()).thenReturn(Optional.of(userId));
        when(loadUserPort.loadById(userId))
                .thenReturn(Optional.of(User.rebuild(
                        userId,
                        new EmailAddress("owner@flowops.local"),
                        com.flowops.auth.domain.enums.AccountState.ACTIVE,
                        com.flowops.auth.domain.model.RoleName.OWNER,
                        NOW,
                        true,
                        null)));
        when(loadCredentialPort.loadFor(userId))
                .thenReturn(Optional.of(Credential.issue(userId, CURRENT_PASSWORD, hasher, NOW)));
    }

    private void andTheLocationResolves() {
        when(resolveCoarseLocationPort.resolve(ADDRESS)).thenReturn(SessionMetadata.UNKNOWN);
    }

    private AuthEvent appendedEvent() {
        ArgumentCaptor<AuthEvent> appended = ArgumentCaptor.forClass(AuthEvent.class);
        verify(appendAuthEventPort).append(appended.capture());
        return appended.getValue();
    }

    private ChangePasswordCommand command(String newPassword) {
        return new ChangePasswordCommand(newPassword, new ClientContext(ADDRESS, USER_AGENT));
    }
}
