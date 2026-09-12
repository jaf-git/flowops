package com.flowops.auth.application.requestsignuppasscode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowops.auth.application.shared.AttemptPurpose;
import com.flowops.auth.application.shared.AuthProperties;
import com.flowops.auth.application.shared.ClientContext;
import com.flowops.auth.application.shared.SessionMetadataFactory;
import com.flowops.auth.application.shared.exception.RateLimitExceededException;
import com.flowops.auth.application.shared.exception.SignupClosedException;
import com.flowops.auth.application.shared.port.AppendAuthEventPort;
import com.flowops.auth.application.shared.port.GeneratePasscodePort;
import com.flowops.auth.application.shared.port.LoadUserPort;
import com.flowops.auth.application.shared.port.RateLimitPort;
import com.flowops.auth.application.shared.port.ResolveCoarseLocationPort;
import com.flowops.auth.application.shared.port.SavePasscodePort;
import com.flowops.auth.application.shared.port.SendPasscodePort;
import com.flowops.auth.domain.enums.AuthAction;
import com.flowops.auth.domain.event.AuthEvent;
import com.flowops.auth.domain.model.EmailAddress;
import com.flowops.auth.domain.model.SessionMetadata;
import com.flowops.auth.domain.model.SignupPasscode;
import com.flowops.auth.domain.service.ReversibleHasher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("AUTH-REGISTER-OWNER-01")
@ExtendWith(MockitoExtension.class)
class RequestSignupPasscodeServiceTest {
    private static final String RAW_EMAIL = "founder@flowops.test";
    private static final EmailAddress EMAIL = new EmailAddress(RAW_EMAIL);
    private static final String ADDRESS = "203.0.113.10";
    private static final Instant NOW = Instant.parse("2026-08-02T09:00:00Z");

    @Mock
    private LoadUserPort loadUserPort;

    @Mock
    private SavePasscodePort savePasscodePort;

    @Mock
    private GeneratePasscodePort generatePasscodePort;

    @Mock
    private AppendAuthEventPort appendAuthEventPort;

    @Mock
    private SendPasscodePort sendPasscodePort;

    @Mock
    private RateLimitPort rateLimitPort;

    @Mock
    private ResolveCoarseLocationPort resolveCoarseLocationPort;

    private final ReversibleHasher hasher = new ReversibleHasher();

    private RequestSignupPasscodeService service;

    @BeforeEach
    void setUp() {
        service = new RequestSignupPasscodeService(
                loadUserPort,
                savePasscodePort,
                generatePasscodePort,
                appendAuthEventPort,
                sendPasscodePort,
                rateLimitPort,
                new SessionMetadataFactory(resolveCoarseLocationPort),
                hasher,
                properties(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void aFreeAddressIsSentASinglePasscodeThatExpires() {
        givenAddressIsFree();
        when(generatePasscodePort.generate()).thenReturn("123456");

        service.execute(command());

        ArgumentCaptor<SignupPasscode> saved = ArgumentCaptor.forClass(SignupPasscode.class);
        verify(savePasscodePort).save(saved.capture());
        assertThat(saved.getValue().expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        verify(sendPasscodePort).sendPasscode(EMAIL, "123456");
    }

    @Test
    void issuingAPasscodeIsRecordedAsAnIssueRatherThanAsASignup() {
        givenAddressIsFree();
        when(generatePasscodePort.generate()).thenReturn("123456");

        service.execute(command());

        ArgumentCaptor<AuthEvent> appended = ArgumentCaptor.forClass(AuthEvent.class);
        verify(appendAuthEventPort).append(appended.capture());
        assertThat(appended.getValue().action()).isEqualTo(AuthAction.SIGNUP_PASSCODE_ISSUED);
        verify(sendPasscodePort, never()).sendDuplicateSignupNotice(any());
    }

    @Test
    void aRegisteredAddressIsIssuedNoPasscode() {
        when(loadUserPort.existsByEmail(EMAIL)).thenReturn(true);
        givenLocationIsResolvable();

        service.execute(command());

        verify(savePasscodePort, never()).save(any());
        verify(sendPasscodePort, never()).sendPasscode(any(), anyString());
    }

    @Test
    void aRegisteredAddressReceivesANoticeInsteadSoItsOwnerLearnsOfTheAttempt() {
        when(loadUserPort.existsByEmail(EMAIL)).thenReturn(true);
        givenLocationIsResolvable();

        service.execute(command());

        verify(sendPasscodePort).sendDuplicateSignupNotice(EMAIL);
    }

    @Test
    void aRegisteredAddressIsRecordedAsADuplicateAttempt() {
        when(loadUserPort.existsByEmail(EMAIL)).thenReturn(true);
        givenLocationIsResolvable();

        service.execute(command());

        ArgumentCaptor<AuthEvent> appended = ArgumentCaptor.forClass(AuthEvent.class);
        verify(appendAuthEventPort).append(appended.capture());
        assertThat(appended.getValue().action()).isEqualTo(AuthAction.DUPLICATE_SIGNUP_ATTEMPT);
    }

    @Test
    void bothBranchesCountOneAttemptAndSendOneMailSoNeitherIsDistinguishableByBehaviour() {
        givenAddressIsFree();
        when(generatePasscodePort.generate()).thenReturn("123456");

        service.execute(command());

        verify(rateLimitPort).record(AttemptPurpose.SIGNUP, RAW_EMAIL, ADDRESS);
        verify(appendAuthEventPort).append(any(AuthEvent.class));
        verify(sendPasscodePort).sendPasscode(EMAIL, "123456");

        when(loadUserPort.existsByEmail(EMAIL)).thenReturn(true);

        service.execute(command());

        verify(rateLimitPort, times(2)).record(AttemptPurpose.SIGNUP, RAW_EMAIL, ADDRESS);
        verify(appendAuthEventPort, times(2)).append(any(AuthEvent.class));
        verify(sendPasscodePort).sendDuplicateSignupNotice(EMAIL);
    }

    @Test
    void bothBranchesHashOneCodeSoNeitherAnswersSooner() {
        givenAddressIsFree();
        when(generatePasscodePort.generate()).thenReturn("123456");

        service.execute(command());
        int onAFreeAddress = hasher.hashCalls();

        hasher.resetCounts();
        when(loadUserPort.existsByEmail(EMAIL)).thenReturn(true);

        service.execute(command());

        assertThat(hasher.hashCalls()).isEqualTo(onAFreeAddress).isEqualTo(1);
    }

    @Test
    void attemptsBeyondTheCeilingAreRefusedBeforeAnythingIsIssued() {
        when(rateLimitPort.isLimited(eq(AttemptPurpose.SIGNUP), eq(RAW_EMAIL), eq(ADDRESS)))
                .thenReturn(true);
        givenLocationIsResolvable();

        assertThatThrownBy(() -> service.execute(command())).isInstanceOf(RateLimitExceededException.class);

        verify(savePasscodePort, never()).save(any());
        verify(sendPasscodePort, never()).sendPasscode(any(), anyString());
    }

    @Test
    void aClaimedInstallationIssuesNoPasscodeAndCreatesNothing() {
        when(loadUserPort.anOwnerExists()).thenReturn(true);

        assertThatThrownBy(() -> service.execute(command())).isInstanceOf(SignupClosedException.class);

        verify(savePasscodePort, never()).save(any());
        verify(sendPasscodePort, never()).sendPasscode(any(), anyString());
        verify(sendPasscodePort, never()).sendDuplicateSignupNotice(any());
        verify(appendAuthEventPort, never()).append(any());
    }

    @Test
    void theOwnerCheckRunsBeforeTheAddressIsExaminedAtAll() {
        when(loadUserPort.anOwnerExists()).thenReturn(true);

        assertThatThrownBy(() -> service.execute(command())).isInstanceOf(SignupClosedException.class);

        verify(loadUserPort, never()).existsByEmail(any());
        verify(rateLimitPort, never()).isLimited(any(), anyString(), anyString());
        verify(rateLimitPort, never()).record(any(), anyString(), anyString());
        verify(generatePasscodePort, never()).generate();
    }

    @Test
    void aClaimedInstallationAnswersTheSameForAnyAddressAtAll() {
        when(loadUserPort.anOwnerExists()).thenReturn(true);

        assertThatThrownBy(() -> service.execute(
                        new RequestSignupPasscodeCommand("not-an-address", new ClientContext(ADDRESS, "agent"))))
                .isInstanceOf(SignupClosedException.class);
    }

    private void givenAddressIsFree() {
        when(loadUserPort.existsByEmail(EMAIL)).thenReturn(false);
        givenLocationIsResolvable();
    }

    private void givenLocationIsResolvable() {
        when(resolveCoarseLocationPort.resolve(ADDRESS)).thenReturn(SessionMetadata.UNKNOWN);
    }

    private RequestSignupPasscodeCommand command() {
        return new RequestSignupPasscodeCommand(RAW_EMAIL, new ClientContext(ADDRESS, "Mozilla/5.0 (Windows NT 10.0)"));
    }

    private AuthProperties properties() {
        return new AuthProperties(
                Duration.ofMinutes(10),
                5,
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
