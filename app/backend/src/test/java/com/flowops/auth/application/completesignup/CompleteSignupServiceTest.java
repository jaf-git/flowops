package com.flowops.auth.application.completesignup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowops.auth.application.shared.AttemptPurpose;
import com.flowops.auth.application.shared.AuthProperties;
import com.flowops.auth.application.shared.ClientContext;
import com.flowops.auth.application.shared.SessionContext;
import com.flowops.auth.application.shared.SessionMetadataFactory;
import com.flowops.auth.application.shared.exception.PasscodeAttemptLockedException;
import com.flowops.auth.application.shared.exception.PasscodeRejectedException;
import com.flowops.auth.application.shared.exception.RateLimitExceededException;
import com.flowops.auth.application.shared.exception.SignupClosedException;
import com.flowops.auth.application.shared.port.AppendAuthEventPort;
import com.flowops.auth.application.shared.port.LoadPasscodePort;
import com.flowops.auth.application.shared.port.LoadUserPort;
import com.flowops.auth.application.shared.port.RateLimitPort;
import com.flowops.auth.application.shared.port.ResolveCoarseLocationPort;
import com.flowops.auth.application.shared.port.ResolvePermissionsPort;
import com.flowops.auth.application.shared.port.SaveCredentialPort;
import com.flowops.auth.application.shared.port.SavePasscodePort;
import com.flowops.auth.application.shared.port.SaveUserPort;
import com.flowops.auth.application.shared.port.SessionRegistryPort;
import com.flowops.auth.domain.enums.AuthAction;
import com.flowops.auth.domain.enums.PasswordRule;
import com.flowops.auth.domain.event.AuthEvent;
import com.flowops.auth.domain.exception.PasswordPolicyViolationException;
import com.flowops.auth.domain.model.AuthPermissions;
import com.flowops.auth.domain.model.Credential;
import com.flowops.auth.domain.model.EmailAddress;
import com.flowops.auth.domain.model.RoleName;
import com.flowops.auth.domain.model.SessionMetadata;
import com.flowops.auth.domain.model.SignupPasscode;
import com.flowops.auth.domain.model.User;
import com.flowops.auth.domain.service.PasswordPolicy;
import com.flowops.auth.domain.service.ReversibleHasher;
import java.time.Clock;
import java.time.Duration;
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

@Tag("AUTH-REGISTER-OWNER-01")
@ExtendWith(MockitoExtension.class)
class CompleteSignupServiceTest {
    private static final String RAW_EMAIL = "founder@flowops.test";
    private static final EmailAddress EMAIL = new EmailAddress(RAW_EMAIL);
    private static final String ADDRESS = "203.0.113.10";
    private static final String CODE = "123456";
    private static final String PASSWORD = "correcthorsebattery";
    private static final Instant NOW = Instant.parse("2026-08-02T09:00:00Z");
    private static final int CEILING = 5;

    private final ReversibleHasher hasher = new ReversibleHasher();

    @Mock
    private LoadUserPort loadUserPort;

    @Mock
    private SaveUserPort saveUserPort;

    @Mock
    private LoadPasscodePort loadPasscodePort;

    @Mock
    private SavePasscodePort savePasscodePort;

    @Mock
    private SaveCredentialPort saveCredentialPort;

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

    private CompleteSignupService service;

    @BeforeEach
    void setUp() {
        service = new CompleteSignupService(
                loadUserPort,
                saveUserPort,
                loadPasscodePort,
                savePasscodePort,
                saveCredentialPort,
                sessionRegistryPort,
                resolvePermissionsPort,
                appendAuthEventPort,
                rateLimitPort,
                new SessionMetadataFactory(resolveCoarseLocationPort),
                hasher,
                new PasswordPolicy(),
                properties(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        lenient().when(resolveCoarseLocationPort.resolve(ADDRESS)).thenReturn(SessionMetadata.UNKNOWN);
    }

    @Test
    void aValidPasscodeAndPolicyCompliantPasswordCreateTheOwner() {
        givenLivePasscode();
        givenPermissionsResolve();

        SessionContext context = service.execute(command(CODE, PASSWORD));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(saveUserPort).save(saved.capture());
        assertThat(saved.getValue().role()).isEqualTo(RoleName.OWNER);
        assertThat(context.email()).isEqualTo(RAW_EMAIL);
    }

    @Test
    void theCredentialIsStoredAsAHashAndNeverAsThePassword() {
        givenLivePasscode();
        givenPermissionsResolve();

        service.execute(command(CODE, PASSWORD));

        ArgumentCaptor<Credential> saved = ArgumentCaptor.forClass(Credential.class);
        verify(saveCredentialPort).save(saved.capture());
        assertThat(saved.getValue().passwordHash()).isNotEqualTo(PASSWORD);
    }

    @Test
    void aSessionIsOpenedAndTheCompletionIsRecorded() {
        givenLivePasscode();
        givenPermissionsResolve();

        service.execute(command(CODE, PASSWORD));

        verify(sessionRegistryPort).open(any(User.class), any(SessionMetadata.class));
        ArgumentCaptor<AuthEvent> appended = ArgumentCaptor.forClass(AuthEvent.class);
        verify(appendAuthEventPort).append(appended.capture());
        assertThat(appended.getValue().action()).isEqualTo(AuthAction.SIGNUP_COMPLETED);
    }

    @Test
    void thePasscodeIsSpentSoItCannotBeReplayed() {
        givenLivePasscode();
        givenPermissionsResolve();

        service.execute(command(CODE, PASSWORD));

        ArgumentCaptor<SignupPasscode> saved = ArgumentCaptor.forClass(SignupPasscode.class);
        verify(savePasscodePort).save(saved.capture());
        assertThat(saved.getValue().used()).isTrue();
    }

    @Test
    void aWrongPasscodeCreatesNoAccount() {
        givenLivePasscode();

        assertThatThrownBy(() -> service.execute(command("654321", PASSWORD)))
                .isInstanceOf(PasscodeRejectedException.class);

        verify(saveUserPort, never()).save(any());
    }

    @Test
    void aWrongPasscodeIsRecordedAsAFailure() {
        givenLivePasscode();

        assertThatThrownBy(() -> service.execute(command("654321", PASSWORD)))
                .isInstanceOf(PasscodeRejectedException.class);

        ArgumentCaptor<AuthEvent> appended = ArgumentCaptor.forClass(AuthEvent.class);
        verify(appendAuthEventPort).append(appended.capture());
        assertThat(appended.getValue().action()).isEqualTo(AuthAction.SIGNUP_FAILED);
    }

    @Test
    void aWrongPasscodeCountsTowardTheCeilingAndThatCountIsPersisted() {
        givenLivePasscode();

        assertThatThrownBy(() -> service.execute(command("654321", PASSWORD)))
                .isInstanceOf(PasscodeRejectedException.class);

        ArgumentCaptor<SignupPasscode> saved = ArgumentCaptor.forClass(SignupPasscode.class);
        verify(savePasscodePort).save(saved.capture());
        assertThat(saved.getValue().failureCount()).isEqualTo(1);
    }

    @Test
    void anExpiredPasscodeIsRefused() {
        SignupPasscode expired =
                SignupPasscode.issue(EMAIL, CODE, hasher, NOW.minus(Duration.ofHours(1)), Duration.ofMinutes(10));
        when(loadPasscodePort.loadLatestFor(EMAIL)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.execute(command(CODE, PASSWORD)))
                .isInstanceOf(PasscodeRejectedException.class);

        verify(saveUserPort, never()).save(any());
    }

    @Test
    void aPasscodeThatWasNeverIssuedIsRefusedTheSameWayAsAWrongOne() {
        when(loadPasscodePort.loadLatestFor(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(command(CODE, PASSWORD)))
                .isInstanceOf(PasscodeRejectedException.class);
    }

    @Test
    void failuresBeyondTheCeilingLockTheAttemptUntilAFreshPasscodeIsRequested() {
        SignupPasscode exhausted = SignupPasscode.rebuild(
                java.util.UUID.randomUUID(),
                EMAIL,
                hasher.hash(CODE),
                NOW,
                NOW.plus(Duration.ofMinutes(10)),
                false,
                CEILING);
        when(loadPasscodePort.loadLatestFor(EMAIL)).thenReturn(Optional.of(exhausted));

        assertThatThrownBy(() -> service.execute(command(CODE, PASSWORD)))
                .isInstanceOf(PasscodeAttemptLockedException.class);

        verify(saveUserPort, never()).save(any());
    }

    @Test
    void aPasswordFailingThePolicyIsRejectedNamingTheUnmetRule() {
        givenLivePasscode();

        assertThatThrownBy(() -> service.execute(command(CODE, "short")))
                .isInstanceOf(PasswordPolicyViolationException.class)
                .extracting(thrown -> ((PasswordPolicyViolationException) thrown).violations())
                .isEqualTo(java.util.List.of(PasswordRule.MINIMUM_LENGTH));
    }

    @Test
    void aPasswordFailingThePolicyCreatesNoAccount() {
        givenLivePasscode();

        assertThatThrownBy(() -> service.execute(command(CODE, "short")))
                .isInstanceOf(PasswordPolicyViolationException.class);

        verify(saveUserPort, never()).save(any());
    }

    private void givenLivePasscode() {
        when(loadPasscodePort.loadLatestFor(EMAIL))
                .thenReturn(Optional.of(SignupPasscode.issue(EMAIL, CODE, hasher, NOW, Duration.ofMinutes(10))));
    }

    @Test
    void attemptsBeyondTheLimitAreRefusedBeforeThePasscodeIsEvenRead() {
        when(rateLimitPort.isLimited(AttemptPurpose.SIGNUP_COMPLETION, RAW_EMAIL, ADDRESS))
                .thenReturn(true);

        assertThatThrownBy(() -> service.execute(command(CODE, PASSWORD)))
                .isInstanceOf(RateLimitExceededException.class);

        verify(loadPasscodePort, never()).loadLatestFor(any());
        verify(saveUserPort, never()).save(any());
    }

    @Test
    void aRefusedAttemptIsCountedTowardTheCompletionLimitEvenWithNoPasscodeAtAll() {
        when(loadPasscodePort.loadLatestFor(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(command(CODE, PASSWORD)))
                .isInstanceOf(PasscodeRejectedException.class);

        verify(rateLimitPort).record(AttemptPurpose.SIGNUP_COMPLETION, RAW_EMAIL, ADDRESS);
    }

    @Test
    void aSuccessfulSignupCountsNothingAgainstTheLimit() {
        givenLivePasscode();
        givenPermissionsResolve();

        service.execute(command(CODE, PASSWORD));

        verify(rateLimitPort, never()).record(any(), any(), any());
    }

    @Test
    void aClaimedInstallationCompletesNoSignupAndCreatesNoAccount() {
        when(loadUserPort.anOwnerExists()).thenReturn(true);

        assertThatThrownBy(() -> service.execute(command(CODE, PASSWORD))).isInstanceOf(SignupClosedException.class);

        verify(saveUserPort, never()).save(any());
        verify(saveCredentialPort, never()).save(any());
        verify(sessionRegistryPort, never()).open(any(), any());
    }

    @Test
    void theOwnerCheckRunsBeforeTheAddressOrThePasscodeIsExamined() {
        when(loadUserPort.anOwnerExists()).thenReturn(true);

        assertThatThrownBy(() -> service.execute(command(CODE, PASSWORD))).isInstanceOf(SignupClosedException.class);

        verify(loadPasscodePort, never()).loadLatestFor(any());
        verify(loadUserPort, never()).existsByEmail(any());
        verify(rateLimitPort, never()).isLimited(any(), any(), any());
        verify(appendAuthEventPort, never()).append(any());
    }

    private void givenPermissionsResolve() {
        when(loadUserPort.existsByEmail(EMAIL)).thenReturn(false);
        when(resolvePermissionsPort.resolveFor(any())).thenReturn(Set.of(AuthPermissions.SESSION_VIEW_OWN));
    }

    private CompleteSignupCommand command(String passcode, String password) {
        return new CompleteSignupCommand(
                RAW_EMAIL, passcode, password, new ClientContext(ADDRESS, "Mozilla/5.0 (Windows NT 10.0)"));
    }

    private AuthProperties properties() {
        return new AuthProperties(
                Duration.ofMinutes(10),
                CEILING,
                Duration.ofMinutes(15),
                Duration.ofMinutes(15),
                10,
                30,
                Duration.ofMinutes(60),
                Duration.ofHours(24),
                3,
                20,
                "http://localhost:5173/reset-password",
                "no-reply@flowops.local");
    }
}
