package com.flowops.auth.application.viewusersessions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowops.auth.application.shared.ClientContext;
import com.flowops.auth.application.shared.SessionMetadataFactory;
import com.flowops.auth.application.shared.exception.AuthenticationRefusedException;
import com.flowops.auth.application.shared.port.AppendAuthEventPort;
import com.flowops.auth.application.shared.port.ResolveCoarseLocationPort;
import com.flowops.auth.application.shared.port.SessionRegistryPort;
import com.flowops.auth.domain.enums.AuthAction;
import com.flowops.auth.domain.event.AuthEvent;
import com.flowops.auth.domain.model.ActiveSession;
import com.flowops.auth.domain.model.SessionMetadata;
import com.flowops.auth.domain.model.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("AUTH-TERMINATE-SESSION-01")
@ExtendWith(MockitoExtension.class)
class ViewUserSessionsServiceTest {
    private static final String ADDRESS = "203.0.113.10";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0) Chrome/120.0";
    private static final Instant NOW = Instant.parse("2026-08-03T09:00:00Z");

    @Mock
    private SessionRegistryPort sessionRegistryPort;

    @Mock
    private AppendAuthEventPort appendAuthEventPort;

    @Mock
    private ResolveCoarseLocationPort resolveCoarseLocationPort;

    private UserId owner;
    private ViewUserSessionsService service;

    @BeforeEach
    void setUp() {
        owner = UserId.generate();
        service = new ViewUserSessionsService(
                sessionRegistryPort,
                appendAuthEventPort,
                new SessionMetadataFactory(resolveCoarseLocationPort),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void theSubjectIsThePersonAskedForRatherThanThePersonAsking() {
        UserId subject = UserId.generate();
        givenTheCallerIsSignedIn();
        andTheLocationResolves();
        when(sessionRegistryPort.sessionsOf(subject)).thenReturn(List.of(session()));

        service.execute(query(subject));

        verify(sessionRegistryPort).sessionsOf(subject);
    }

    @Test
    void thatPersonsSessionsAreReturned() {
        UserId subject = UserId.generate();
        ActiveSession theirs = session();
        givenTheCallerIsSignedIn();
        andTheLocationResolves();
        when(sessionRegistryPort.sessionsOf(subject)).thenReturn(List.of(theirs));

        assertThat(service.execute(query(subject))).containsExactly(theirs);
    }

    @Test
    void readingSomeonesSessionsIsRecordedWithActorAndTarget() {
        UserId subject = UserId.generate();
        givenTheCallerIsSignedIn();
        andTheLocationResolves();
        when(sessionRegistryPort.sessionsOf(subject)).thenReturn(List.of(session()));

        service.execute(query(subject));

        AuthEvent recorded = appendedEvent();
        assertThat(recorded.action()).isEqualTo(AuthAction.SESSIONS_OF_PERSON_LISTED);
        assertThat(recorded.actor()).contains(owner);
        assertThat(recorded.target()).contains(subject);
    }

    @Test
    void aPersonWithNoLiveSessionsIsAnEmptyListAndStillARecordedRead() {
        UserId subject = UserId.generate();
        givenTheCallerIsSignedIn();
        andTheLocationResolves();
        when(sessionRegistryPort.sessionsOf(subject)).thenReturn(List.of());

        assertThat(service.execute(query(subject))).isEmpty();
        assertThat(appendedEvent().action()).isEqualTo(AuthAction.SESSIONS_OF_PERSON_LISTED);
    }

    @Test
    void aCallerWithNoLiveSessionIsRefusedAndNothingIsReadOrRecorded() {
        when(sessionRegistryPort.currentUserId()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(query(UserId.generate())))
                .isInstanceOf(AuthenticationRefusedException.class);

        verify(sessionRegistryPort, never()).sessionsOf(any());
        verify(appendAuthEventPort, never()).append(any());
    }

    private void givenTheCallerIsSignedIn() {
        when(sessionRegistryPort.currentUserId()).thenReturn(Optional.of(owner));
    }

    private void andTheLocationResolves() {
        when(resolveCoarseLocationPort.resolve(ADDRESS)).thenReturn(SessionMetadata.UNKNOWN);
    }

    private AuthEvent appendedEvent() {
        ArgumentCaptor<AuthEvent> appended = ArgumentCaptor.forClass(AuthEvent.class);
        verify(appendAuthEventPort).append(appended.capture());
        return appended.getValue();
    }

    private ViewUserSessionsQuery query(UserId subject) {
        return new ViewUserSessionsQuery(subject, new ClientContext(ADDRESS, USER_AGENT));
    }

    private ActiveSession session() {
        return new ActiveSession(
                UUID.randomUUID(),
                NOW,
                NOW,
                new SessionMetadata("198.51.100.7", "Firefox on Linux", SessionMetadata.UNKNOWN),
                false);
    }
}
