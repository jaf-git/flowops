package com.flowops.auth.infrastructure.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowops.auth.application.shared.port.ResolvePermissionsPort;
import com.flowops.auth.domain.model.UserId;
import com.flowops.auth.infrastructure.persistence.repository.AuthSessionMetadataJpaRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

@Tag("AUTH-TERMINATE-SESSION-01")
class SpringSessionRegistryAdapterTest {
    private static final Instant NOW = Instant.parse("2026-08-03T09:00:00Z");

    private MockHttpServletRequest request;
    private MockHttpSession callersSession;
    private AuthSessionMetadataJpaRepository sessionMetadataRepository;
    private FindByIndexNameSessionRepository<Session> sessionsByPrincipal;
    private SpringSessionRegistryAdapter adapter;

    private static final UserId THEIR_SESSIONS_ARE = UserId.generate();

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        callersSession = new MockHttpSession();
        request.setSession(callersSession);
        sessionMetadataRepository = mock(AuthSessionMetadataJpaRepository.class);
        sessionsByPrincipal = mock(FindByIndexNameSessionRepository.class);
        when(sessionsByPrincipal.findByPrincipalName(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Map.of());
        lenient()
                .when(sessionsByPrincipal.findByPrincipalName(
                        THEIR_SESSIONS_ARE.value().toString()))
                .thenReturn(Map.of("a-session", theirSession(), "another-session", theirSession()));

        adapter = new SpringSessionRegistryAdapter(
                request,
                new MockHttpServletResponse(),
                mock(SecurityContextRepository.class),
                sessionMetadataRepository,
                sessionsByPrincipal,
                mock(ResolvePermissionsPort.class),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void clearTheContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void everySessionTheTargetHoldsIsDeleted() {
        signedInAs();

        adapter.endEverySessionFor(THEIR_SESSIONS_ARE);

        verify(sessionsByPrincipal).deleteById("a-session");
        verify(sessionsByPrincipal).deleteById("another-session");
    }

    @Test
    void endingSomeoneElsesSessionsLeavesTheCallerSignedIn() {
        UserId caller = signedInAs();
        UserId somebodyElse = UserId.generate();

        adapter.endEverySessionFor(somebodyElse);

        assertThat(callersSession.isInvalid())
                .as("the caller's own session must survive ending another person's")
                .isFalse();
        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .as("and the caller must still be authenticated")
                .isNotNull();
        assertThat(caller).isNotEqualTo(somebodyElse);
    }

    @Test
    void endingOwnSessionsStillEndsTheOneTheRequestArrivedOn() {
        UserId caller = signedInAs();

        adapter.endEverySessionFor(caller);

        assertThat(callersSession.isInvalid())
                .as("the caller's own session must go when the caller is the target")
                .isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private UserId signedInAs() {
        UserId caller = UserId.generate();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        new SessionPrincipal(caller.value(), "maria@atelier.ro"), null, Set.of()));
        return caller;
    }

    private Session theirSession() {
        return mock(Session.class);
    }
}
