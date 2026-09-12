package com.flowops.auth.application.requestpasswordreset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.flowops.auth.application.shared.AttemptPurpose;
import com.flowops.auth.application.shared.AuthProperties;
import com.flowops.auth.application.shared.ClientContext;
import com.flowops.auth.application.shared.SessionMetadataFactory;
import com.flowops.auth.application.shared.port.AppendAuthEventPort;
import com.flowops.auth.application.shared.port.GenerateResetTokenPort;
import com.flowops.auth.application.shared.port.LoadUserPort;
import com.flowops.auth.application.shared.port.RateLimitPort;
import com.flowops.auth.application.shared.port.ResolveCoarseLocationPort;
import com.flowops.auth.application.shared.port.SaveResetTokenPort;
import com.flowops.auth.application.shared.port.SendResetLinkPort;
import com.flowops.auth.domain.enums.AccountState;
import com.flowops.auth.domain.enums.AuthAction;
import com.flowops.auth.domain.event.AuthEvent;
import com.flowops.auth.domain.model.EmailAddress;
import com.flowops.auth.domain.model.ResetToken;
import com.flowops.auth.domain.model.RoleName;
import com.flowops.auth.domain.model.SessionMetadata;
import com.flowops.auth.domain.model.User;
import com.flowops.auth.domain.model.UserId;
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
class RequestPasswordResetServiceTest {
    private static final String RAW_EMAIL = "maria@atelier.ro";
    private static final EmailAddress EMAIL = new EmailAddress(RAW_EMAIL);
    private static final String ADDRESS = "203.0.113.10";
    private static final String RAW_TOKEN = "a-long-unguessable-token";
    private static final String TOKEN_HASH = "the-hash-of-the-token-under-test";
    private static final Instant NOW = Instant.parse("2026-08-08T09:00:00Z");

    @Mock
    private LoadUserPort loadUserPort;

    @Mock
    private SaveResetTokenPort saveResetTokenPort;

    @Mock
    private GenerateResetTokenPort generateResetTokenPort;

    @Mock
    private AppendAuthEventPort appendAuthEventPort;

    @Mock
    private SendResetLinkPort sendResetLinkPort;

    @Mock
    private RateLimitPort rateLimitPort;

    @Mock
    private ResolveCoarseLocationPort resolveCoarseLocationPort;

    private RequestPasswordResetService service;

    @BeforeEach
    void setUp() {
        service = new RequestPasswordResetService(
                loadUserPort,
                saveResetTokenPort,
                generateResetTokenPort,
                appendAuthEventPort,
                sendResetLinkPort,
                rateLimitPort,
                new SessionMetadataFactory(resolveCoarseLocationPort),
                properties(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(resolveCoarseLocationPort.resolve(ADDRESS)).thenReturn(SessionMetadata.UNKNOWN);
    }

    @Test
    void anActiveAccountIsIssuedASingleUseTokenAndSentTheLink() {
        givenAnAccountThatIs(AccountState.ACTIVE);
        when(generateResetTokenPort.mint()).thenReturn(minted());

        service.execute(command());

        ArgumentCaptor<ResetToken> saved = ArgumentCaptor.forClass(ResetToken.class);
        verify(saveResetTokenPort).save(saved.capture());
        assertThat(saved.getValue().isUsable(NOW)).isTrue();
        assertThat(saved.getValue().expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(60)));
        verify(sendResetLinkPort).sendResetLink(EMAIL, RAW_TOKEN);
    }

    @Test
    void whatIsStoredIsTheHashAndNotTheTokenItself() {
        givenAnAccountThatIs(AccountState.ACTIVE);
        when(generateResetTokenPort.mint()).thenReturn(minted());

        service.execute(command());

        ArgumentCaptor<ResetToken> saved = ArgumentCaptor.forClass(ResetToken.class);
        verify(saveResetTokenPort).save(saved.capture());
        assertThat(saved.getValue().tokenHash()).doesNotContain(RAW_TOKEN).isEqualTo(TOKEN_HASH);
    }

    @Test
    void theThreeAddressBranchesPerformTheSameCountedWork() {
        when(generateResetTokenPort.mint()).thenReturn(minted());

        givenAnAccountThatIs(AccountState.ACTIVE);
        service.execute(command());
        assertEveryOtherPortWasTouchedExactlyOnce();

        reset(loadUserPort, appendAuthEventPort, rateLimitPort, generateResetTokenPort);
        when(generateResetTokenPort.mint()).thenReturn(minted());
        givenAnAccountThatIs(AccountState.DEACTIVATED);
        service.execute(command());
        assertEveryOtherPortWasTouchedExactlyOnce();

        reset(loadUserPort, appendAuthEventPort, rateLimitPort, generateResetTokenPort);
        when(generateResetTokenPort.mint()).thenReturn(minted());
        when(loadUserPort.loadByEmail(EMAIL)).thenReturn(Optional.empty());
        service.execute(command());
        assertEveryOtherPortWasTouchedExactlyOnce();
    }

    @Test
    void anAddressWithNoAccountMintsNothingAndSendsNothing() {
        when(loadUserPort.loadByEmail(EMAIL)).thenReturn(Optional.empty());
        when(generateResetTokenPort.mint()).thenReturn(minted());

        service.execute(command());

        verify(saveResetTokenPort, never()).save(any());
        verify(sendResetLinkPort, never()).sendResetLink(any(), anyString());
    }

    @Test
    void aDeactivatedAccountMintsNothingAndSendsNothing() {
        givenAnAccountThatIs(AccountState.DEACTIVATED);
        when(generateResetTokenPort.mint()).thenReturn(minted());

        service.execute(command());

        verify(saveResetTokenPort, never()).save(any());
        verify(sendResetLinkPort, never()).sendResetLink(any(), anyString());
    }

    @Test
    void aLimitedRequestIsRefusedSilentlyRatherThanWithAStatusSomebodyCanSee() {
        when(rateLimitPort.isLimited(AttemptPurpose.PASSWORD_RESET, RAW_EMAIL, ADDRESS))
                .thenReturn(true);

        assertThatCode(() -> service.execute(command())).doesNotThrowAnyException();

        verify(saveResetTokenPort, never()).save(any());
        verify(sendResetLinkPort, never()).sendResetLink(any(), anyString());
        verify(loadUserPort, never()).loadByEmail(any());
        verify(generateResetTokenPort, never()).mint();
    }

    @Test
    void requestingAResetAppendsTheRequestEventAndNothingAboutSessions() {
        givenAnAccountThatIs(AccountState.ACTIVE);
        when(generateResetTokenPort.mint()).thenReturn(minted());

        service.execute(command());

        ArgumentCaptor<AuthEvent> appended = ArgumentCaptor.forClass(AuthEvent.class);
        verify(appendAuthEventPort).append(appended.capture());
        assertThat(appended.getValue().action()).isEqualTo(AuthAction.PASSWORD_RESET_REQUESTED);
    }

    private void assertEveryOtherPortWasTouchedExactlyOnce() {
        verify(rateLimitPort).isLimited(AttemptPurpose.PASSWORD_RESET, RAW_EMAIL, ADDRESS);
        verify(rateLimitPort).record(AttemptPurpose.PASSWORD_RESET, RAW_EMAIL, ADDRESS);
        verify(loadUserPort).loadByEmail(EMAIL);
        verify(generateResetTokenPort).mint();
        verify(appendAuthEventPort).append(any(AuthEvent.class));
        verifyNoMoreInteractions(loadUserPort, appendAuthEventPort, rateLimitPort, generateResetTokenPort);
    }

    private void givenAnAccountThatIs(AccountState state) {
        when(loadUserPort.loadByEmail(EMAIL))
                .thenReturn(
                        Optional.of(User.rebuild(UserId.generate(), EMAIL, state, RoleName.OWNER, NOW, true, null)));
    }

    private GenerateResetTokenPort.MintedResetToken minted() {
        return new GenerateResetTokenPort.MintedResetToken(RAW_TOKEN, TOKEN_HASH);
    }

    private RequestPasswordResetCommand command() {
        return new RequestPasswordResetCommand(
                RAW_EMAIL, new ClientContext(ADDRESS, "Mozilla/5.0 (Windows NT 10.0) Chrome/120.0"));
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
                "no-reply@flowops.test");
    }
}
