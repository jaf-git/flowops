package com.flowops.auth.application.terminatesession;

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
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("AUTH-TERMINATE-SESSION-01")
@ExtendWith(MockitoExtension.class)
class TerminateSessionServiceTest {
    private static final String ADDRESS = "203.0.113.10";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0) Chrome/120.0";
    private static final Instant NOW = Instant.parse("2026-08-02T09:00:00Z");

    @Mock
    private SessionRegistryPort sessionRegistryPort;

    @Mock
    private AppendAuthEventPort appendAuthEventPort;

    @Mock
    private SensitiveActionGuard sensitiveActionGuard;

    @Mock
    private ResolveCoarseLocationPort resolveCoarseLocationPort;

    private UserId owner;
    private UserId affectedPerson;
    private UUID reference;
    private TerminateSessionService service;

    @BeforeEach
    void setUp() {
        owner = UserId.generate();
        affectedPerson = UserId.generate();
        reference = UUID.randomUUID();
        service = new TerminateSessionService(
                sessionRegistryPort,
                appendAuthEventPort,
                sensitiveActionGuard,
                new SessionMetadataFactory(resolveCoarseLocationPort),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void theReferencedSessionIsEnded() {
        givenTheOwnerIsReauthenticated();
        andTheSessionBelongsTo(affectedPerson);
        andTheLocationResolves();

        service.execute(command());

        verify(sessionRegistryPort).endByReference(reference);
    }

    @Test
    void theTerminationRecordsWhoEndedWhose() {
        givenTheOwnerIsReauthenticated();
        andTheSessionBelongsTo(affectedPerson);
        andTheLocationResolves();

        service.execute(command());

        AuthEvent recorded = appendedEvent();
        assertThat(recorded.action()).isEqualTo(AuthAction.SESSION_TERMINATED);
        assertThat(recorded.actor()).contains(owner);
        assertThat(recorded.target()).contains(affectedPerson);
    }

    @Test
    void theSessionEndsBeforeTheEventIsAppended() {
        givenTheOwnerIsReauthenticated();
        andTheSessionBelongsTo(affectedPerson);
        andTheLocationResolves();

        service.execute(command());

        var order = Mockito.inOrder(sessionRegistryPort, appendAuthEventPort);
        order.verify(sessionRegistryPort).endByReference(reference);
        order.verify(appendAuthEventPort).append(any(AuthEvent.class));
    }

    @Test
    void aSessionThatHasAlreadyEndedSucceedsWithoutError() {
        givenTheOwnerIsReauthenticated();
        andTheLocationResolves();
        when(sessionRegistryPort.endByReference(reference)).thenReturn(Optional.empty());

        service.execute(command());

        verify(sessionRegistryPort).endByReference(reference);
    }

    @Test
    void aSessionThatHasAlreadyEndedIsRecordedAsAnAttemptAndNeverAsATermination() {
        givenTheOwnerIsReauthenticated();
        andTheLocationResolves();
        when(sessionRegistryPort.endByReference(reference)).thenReturn(Optional.empty());

        service.execute(command());

        assertThat(appendedEvent().action()).isEqualTo(AuthAction.SESSION_TERMINATION_ATTEMPTED);
        assertThat(appendedEvent().actor()).contains(owner);
    }

    @Test
    void anAttemptOnAReferenceThatStillNamesSomebodyRecordsThatPerson() {
        givenTheOwnerIsReauthenticated();
        andTheLocationResolves();
        when(sessionRegistryPort.ownerOf(reference)).thenReturn(Optional.of(affectedPerson));
        when(sessionRegistryPort.endByReference(reference)).thenReturn(Optional.empty());

        service.execute(command());

        assertThat(appendedEvent().action()).isEqualTo(AuthAction.SESSION_TERMINATION_ATTEMPTED);
        assertThat(appendedEvent().target()).contains(affectedPerson);
    }

    @Test
    void aCallerOutsideTheReauthenticationWindowIsChallengedFirst() {
        when(sessionRegistryPort.currentUserId()).thenReturn(Optional.of(owner));
        doThrow(new ReauthenticationRequiredException())
                .when(sensitiveActionGuard)
                .requireRecentReauthentication();

        assertThatThrownBy(() -> service.execute(command())).isInstanceOf(ReauthenticationRequiredException.class);

        verify(sessionRegistryPort, never()).endByReference(any());
        verify(appendAuthEventPort, never()).append(any());
    }

    @Test
    void aCallerWithNoLiveSessionIsRefusedAndNothingEnds() {
        when(sessionRegistryPort.currentUserId()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(command())).isInstanceOf(AuthenticationRefusedException.class);

        verify(sessionRegistryPort, never()).endByReference(any());
    }

    private void givenTheOwnerIsReauthenticated() {
        when(sessionRegistryPort.currentUserId()).thenReturn(Optional.of(owner));
    }

    private void andTheSessionBelongsTo(UserId person) {
        when(sessionRegistryPort.endByReference(reference)).thenReturn(Optional.of(person));
    }

    private void andTheLocationResolves() {
        when(resolveCoarseLocationPort.resolve(ADDRESS)).thenReturn(SessionMetadata.UNKNOWN);
    }

    private AuthEvent appendedEvent() {
        ArgumentCaptor<AuthEvent> appended = ArgumentCaptor.forClass(AuthEvent.class);
        verify(appendAuthEventPort).append(appended.capture());
        return appended.getValue();
    }

    private TerminateSessionCommand command() {
        return new TerminateSessionCommand(reference, new ClientContext(ADDRESS, USER_AGENT));
    }
}
