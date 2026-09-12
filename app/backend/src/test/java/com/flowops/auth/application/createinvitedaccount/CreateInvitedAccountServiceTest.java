package com.flowops.auth.application.createinvitedaccount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowops.auth.application.shared.ClientContext;
import com.flowops.auth.application.shared.SessionMetadataFactory;
import com.flowops.auth.application.shared.port.AppendAuthEventPort;
import com.flowops.auth.application.shared.port.LoadUserPort;
import com.flowops.auth.application.shared.port.ResolveCoarseLocationPort;
import com.flowops.auth.application.shared.port.ResolvePermissionsPort;
import com.flowops.auth.application.shared.port.SaveCredentialPort;
import com.flowops.auth.application.shared.port.SaveUserPort;
import com.flowops.auth.application.shared.port.SessionRegistryPort;
import com.flowops.auth.domain.model.EmailAddress;
import com.flowops.auth.domain.model.User;
import com.flowops.auth.domain.service.PasswordHasher;
import com.flowops.auth.domain.service.PasswordPolicy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

@Tag("AUTH-ACCEPT-INVITE-01")
class CreateInvitedAccountServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-06T09:00:00Z");
    private static final String GOOD_PASSWORD = "a-long-enough-passphrase";
    private static final EmailAddress COSMIN = new EmailAddress("cosmin.ionescu@atelier.ro");

    private LoadUserPort loadUserPort;
    private SaveUserPort saveUserPort;
    private SaveCredentialPort saveCredentialPort;
    private SessionRegistryPort sessionRegistryPort;
    private AppendAuthEventPort appendAuthEventPort;
    private ResolvePermissionsPort resolvePermissionsPort;
    private CreateInvitedAccountService service;

    @BeforeEach
    void buildTheServiceWithEveryPortDoubled() {
        loadUserPort = Mockito.mock(LoadUserPort.class);
        saveUserPort = Mockito.mock(SaveUserPort.class);
        saveCredentialPort = Mockito.mock(SaveCredentialPort.class);
        sessionRegistryPort = Mockito.mock(SessionRegistryPort.class);
        appendAuthEventPort = Mockito.mock(AppendAuthEventPort.class);
        resolvePermissionsPort = Mockito.mock(ResolvePermissionsPort.class);
        PasswordHasher hasher = Mockito.mock(PasswordHasher.class);
        ResolveCoarseLocationPort location = Mockito.mock(ResolveCoarseLocationPort.class);
        when(location.resolve(any())).thenReturn("unknown");

        when(loadUserPort.existsByEmail(any())).thenReturn(false);
        when(resolvePermissionsPort.resolveFor(any())).thenReturn(Set.of("TASK_VIEW_OWN"));
        when(hasher.hash(any())).thenReturn("a-hash");
        when(hasher.algorithm()).thenReturn("bcrypt");

        service = new CreateInvitedAccountService(
                loadUserPort,
                saveUserPort,
                saveCredentialPort,
                sessionRegistryPort,
                appendAuthEventPort,
                resolvePermissionsPort,
                hasher,
                new PasswordPolicy(),
                new SessionMetadataFactory(location),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void theAccountIsCreatedWithTheInvitedRoleAndTheNameAlreadyOnIt() {
        service.execute(command("Cosmin Ionescu", "EMPLOYEE", GOOD_PASSWORD));

        User created = theSavedUser();
        assertThat(created.email()).isEqualTo(COSMIN);
        assertThat(created.role().value()).isEqualTo("EMPLOYEE");
        assertThat(created.displayName().map(name -> name.value()))
                .as("an account created without a name is dropped from the people directory")
                .contains("Cosmin Ionescu");
    }

    @Test
    void aNameWithRomanianDiacriticsIsStoredExactlyAsItWasTyped() {
        service.execute(command("Ionuț Rădulescu", "MANAGER", GOOD_PASSWORD));

        assertThat(theSavedUser().displayName().map(name -> name.value())).contains("Ionuț Rădulescu");
    }

    @Test
    void theReturnedContextCarriesTheLandingTargetForTheInvitedRole() {
        InvitedAccountCreated context = service.execute(command("Ionuț Rădulescu", "MANAGER", GOOD_PASSWORD));

        assertThat(context.landingTarget()).isEqualTo("TRIAGE");
        assertThat(context.email()).isEqualTo(COSMIN.value());
    }

    @Test
    void aCredentialIsIssuedAndASessionOpenedForTheNewAccount() {
        service.execute(command("Cosmin Ionescu", "EMPLOYEE", GOOD_PASSWORD));

        verify(saveCredentialPort).save(any());
        verify(sessionRegistryPort).open(any(), any());
        verify(appendAuthEventPort).append(any());
    }

    @Test
    void aPasswordFailingPolicyIsRefusedByRuleAndCreatesNothing() {
        assertThatThrownBy(() -> service.execute(command("Cosmin Ionescu", "EMPLOYEE", "short")))
                .isInstanceOf(InvitedPasswordRefusedException.class);

        verify(saveUserPort, never()).save(any());
        verify(saveCredentialPort, never()).save(any());
        verify(sessionRegistryPort, never()).open(any(), any());
    }

    @Test
    void anEmptyDisplayNameIsRefusedAndCreatesNothing() {
        assertThatThrownBy(() -> service.execute(command("   ", "EMPLOYEE", GOOD_PASSWORD)))
                .isInstanceOf(InvitedNameRequiredException.class);

        verify(saveUserPort, never()).save(any());
        verify(sessionRegistryPort, never()).open(any(), any());
    }

    @Test
    void anAddressThatAlreadyHoldsAnAccountIsRefusedRatherThanCollidingWithTheUniqueConstraint() {
        when(loadUserPort.existsByEmail(COSMIN)).thenReturn(true);

        assertThatThrownBy(() -> service.execute(command("Cosmin Ionescu", "EMPLOYEE", GOOD_PASSWORD)))
                .isInstanceOf(InvitedAddressTakenException.class);

        verify(saveUserPort, never()).save(any());
    }

    private User theSavedUser() {
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(saveUserPort).save(saved.capture());
        return saved.getValue();
    }

    private static CreateInvitedAccountCommand command(String displayName, String role, String password) {
        return new CreateInvitedAccountCommand(
                COSMIN.value(), displayName, role, password, new ClientContext("127.0.0.1", "a-browser"));
    }
}
