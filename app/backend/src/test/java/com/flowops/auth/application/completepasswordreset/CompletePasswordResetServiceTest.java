package com.flowops.auth.application.completepasswordreset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowops.auth.application.shared.ClientContext;
import com.flowops.auth.application.shared.SessionMetadataFactory;
import com.flowops.auth.application.shared.UsableResetTokens;
import com.flowops.auth.application.shared.exception.ResetTokenNotUsableException;
import com.flowops.auth.application.shared.port.AppendAuthEventPort;
import com.flowops.auth.application.shared.port.GenerateResetTokenPort;
import com.flowops.auth.application.shared.port.LoadCredentialPort;
import com.flowops.auth.application.shared.port.LoadResetTokenPort;
import com.flowops.auth.application.shared.port.LoadUserPort;
import com.flowops.auth.application.shared.port.ResolveCoarseLocationPort;
import com.flowops.auth.application.shared.port.SaveCredentialPort;
import com.flowops.auth.application.shared.port.SaveResetTokenPort;
import com.flowops.auth.application.shared.port.SessionRegistryPort;
import com.flowops.auth.domain.enums.AccountState;
import com.flowops.auth.domain.enums.AuthAction;
import com.flowops.auth.domain.enums.PasswordRule;
import com.flowops.auth.domain.event.AuthEvent;
import com.flowops.auth.domain.exception.PasswordPolicyViolationException;
import com.flowops.auth.domain.model.Credential;
import com.flowops.auth.domain.model.EmailAddress;
import com.flowops.auth.domain.model.ResetToken;
import com.flowops.auth.domain.model.RoleName;
import com.flowops.auth.domain.model.SessionMetadata;
import com.flowops.auth.domain.model.User;
import com.flowops.auth.domain.model.UserId;
import com.flowops.auth.domain.service.PasswordPolicy;
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

@Tag("AUTH-RESET-PASSWORD-01")
@ExtendWith(MockitoExtension.class)
class CompletePasswordResetServiceTest {
    private static final String RAW_EMAIL = "maria@atelier.ro";
    private static final EmailAddress EMAIL = new EmailAddress(RAW_EMAIL);
    private static final String ADDRESS = "203.0.113.10";
    private static final String CLEAR_TOKEN = "a-long-unguessable-token";
    private static final String TOKEN_HASH = "the-sha-256-of-it";
    private static final String OLD_PASSWORD = "the-one-she-forgot";
    private static final String NEW_PASSWORD = "corect-cal-baterie-capsator";
    private static final Instant NOW = Instant.parse("2026-08-08T09:00:00Z");

    private final ReversibleHasher hasher = new ReversibleHasher();

    @Mock
    private LoadResetTokenPort loadResetTokenPort;

    @Mock
    private SaveResetTokenPort saveResetTokenPort;

    @Mock
    private GenerateResetTokenPort generateResetTokenPort;

    @Mock
    private LoadUserPort loadUserPort;

    @Mock
    private LoadCredentialPort loadCredentialPort;

    @Mock
    private SaveCredentialPort saveCredentialPort;

    @Mock
    private SessionRegistryPort sessionRegistryPort;

    @Mock
    private AppendAuthEventPort appendAuthEventPort;

    @Mock
    private ResolveCoarseLocationPort resolveCoarseLocationPort;

    private UserId userId;
    private CompletePasswordResetService service;

    @BeforeEach
    void setUp() {
        userId = UserId.generate();
        service = new CompletePasswordResetService(
                new UsableResetTokens(generateResetTokenPort, loadResetTokenPort, loadUserPort),
                saveResetTokenPort,
                loadCredentialPort,
                saveCredentialPort,
                sessionRegistryPort,
                appendAuthEventPort,
                new SessionMetadataFactory(resolveCoarseLocationPort),
                new PasswordPolicy(),
                hasher,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void aValidTokenAndAGoodPasswordDoAllFourThings() {
        givenAUsableTokenFor(anActiveAccount());
        andTheResetWillBeCompleted();

        service.execute(command(NEW_PASSWORD));

        ArgumentCaptor<Credential> stored = ArgumentCaptor.forClass(Credential.class);
        verify(saveCredentialPort).save(stored.capture());
        assertThat(stored.getValue().matches(NEW_PASSWORD, hasher)).isTrue();

        ArgumentCaptor<ResetToken> spent = ArgumentCaptor.forClass(ResetToken.class);
        verify(saveResetTokenPort).save(spent.capture());
        assertThat(spent.getValue().isSpent()).isTrue();
        assertThat(spent.getValue().spentAt()).isEqualTo(NOW);

        verify(sessionRegistryPort).endEverySessionFor(userId);

        ArgumentCaptor<AuthEvent> appended = ArgumentCaptor.forClass(AuthEvent.class);
        verify(appendAuthEventPort).append(appended.capture());
        assertThat(appended.getValue().action()).isEqualTo(AuthAction.PASSWORD_RESET_COMPLETED);
    }

    @Test
    void completingAResetDoesNotSignAnybodyIn() {
        givenAUsableTokenFor(anActiveAccount());
        andTheResetWillBeCompleted();

        service.execute(command(NEW_PASSWORD));

        verify(sessionRegistryPort, never()).open(any(), any());
    }

    @Test
    void anUnknownTokenIsRefused() {
        when(generateResetTokenPort.hash(CLEAR_TOKEN)).thenReturn(TOKEN_HASH);
        when(loadResetTokenPort.loadByTokenHash(TOKEN_HASH)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(command(NEW_PASSWORD)))
                .isInstanceOf(ResetTokenNotUsableException.class);
    }

    @Test
    void anExpiredTokenIsRefusedByTheSameExceptionAsAnUnknownOne() {
        givenATokenThatIs(ResetToken.rebuild(
                java.util.UUID.randomUUID(),
                userId,
                TOKEN_HASH,
                NOW.minus(Duration.ofHours(3)),
                NOW.minusSeconds(1),
                null));

        assertThatThrownBy(() -> service.execute(command(NEW_PASSWORD)))
                .isInstanceOf(ResetTokenNotUsableException.class);
    }

    @Test
    void anAlreadySpentTokenIsRefusedByTheSameExceptionAsAnUnknownOne() {
        givenATokenThatIs(ResetToken.rebuild(
                java.util.UUID.randomUUID(),
                userId,
                TOKEN_HASH,
                NOW.minus(Duration.ofMinutes(5)),
                NOW.plus(Duration.ofMinutes(55)),
                NOW.minus(Duration.ofMinutes(1))));

        assertThatThrownBy(() -> service.execute(command(NEW_PASSWORD)))
                .isInstanceOf(ResetTokenNotUsableException.class);
    }

    @Test
    void aPasswordFailingPolicyDoesNotSpendTheTokenAndTheSameLinkStillWorks() {
        givenAUsableTokenFor(anActiveAccount());
        andTheResetWillBeCompleted();

        assertThatThrownBy(() -> service.execute(command("short")))
                .isInstanceOf(PasswordPolicyViolationException.class)
                .extracting(failure -> ((PasswordPolicyViolationException) failure).violations())
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.list(PasswordRule.class))
                .contains(PasswordRule.MINIMUM_LENGTH);

        verify(saveResetTokenPort, never()).save(any());
        verify(saveCredentialPort, never()).save(any());

        service.execute(command(NEW_PASSWORD));

        verify(saveCredentialPort).save(any());
    }

    @Test
    void aPasswordEqualToTheCurrentOneIsRefusedAsUnchanged() {
        givenAUsableTokenFor(anActiveAccount());

        assertThatThrownBy(() -> service.execute(command(OLD_PASSWORD)))
                .isInstanceOf(PasswordPolicyViolationException.class)
                .extracting(failure -> ((PasswordPolicyViolationException) failure).violations())
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.list(PasswordRule.class))
                .contains(PasswordRule.NOT_CURRENT_PASSWORD);

        verify(saveResetTokenPort, never()).save(any());
    }

    @Test
    void aTokenForAnAccountThatIsNoLongerActiveIsRefusedAsUnusable() {
        User deactivated = User.rebuild(userId, EMAIL, AccountState.DEACTIVATED, RoleName.EMPLOYEE, NOW, true, null);
        when(generateResetTokenPort.hash(CLEAR_TOKEN)).thenReturn(TOKEN_HASH);
        when(loadResetTokenPort.loadByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(usableToken()));
        when(loadUserPort.loadById(userId)).thenReturn(Optional.of(deactivated));

        assertThatThrownBy(() -> service.execute(command(NEW_PASSWORD)))
                .isInstanceOf(ResetTokenNotUsableException.class);

        verify(saveCredentialPort, never()).save(any());
    }

    private User anActiveAccount() {
        return User.rebuild(userId, EMAIL, AccountState.ACTIVE, RoleName.OWNER, NOW, true, null);
    }

    private ResetToken usableToken() {
        return ResetToken.rebuild(
                java.util.UUID.randomUUID(),
                userId,
                TOKEN_HASH,
                NOW.minus(Duration.ofMinutes(5)),
                NOW.plus(Duration.ofMinutes(55)),
                null);
    }

    private void givenAUsableTokenFor(User user) {
        when(generateResetTokenPort.hash(CLEAR_TOKEN)).thenReturn(TOKEN_HASH);
        when(loadResetTokenPort.loadByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(usableToken()));
        when(loadUserPort.loadById(userId)).thenReturn(Optional.of(user));
        when(loadCredentialPort.loadFor(userId))
                .thenReturn(Optional.of(Credential.issue(userId, OLD_PASSWORD, hasher, NOW)));
    }

    private void andTheResetWillBeCompleted() {
        when(resolveCoarseLocationPort.resolve(ADDRESS)).thenReturn(SessionMetadata.UNKNOWN);
    }

    private void givenATokenThatIs(ResetToken token) {
        when(generateResetTokenPort.hash(CLEAR_TOKEN)).thenReturn(TOKEN_HASH);
        when(loadResetTokenPort.loadByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(token));
    }

    private CompletePasswordResetCommand command(String newPassword) {
        return new CompletePasswordResetCommand(
                CLEAR_TOKEN, newPassword, new ClientContext(ADDRESS, "Mozilla/5.0 (Windows NT 10.0) Chrome/120.0"));
    }
}
