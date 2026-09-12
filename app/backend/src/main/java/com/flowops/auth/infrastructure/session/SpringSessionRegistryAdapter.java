package com.flowops.auth.infrastructure.session;

import com.flowops.auth.application.shared.port.ResolvePermissionsPort;
import com.flowops.auth.application.shared.port.SessionRegistryPort;
import com.flowops.auth.domain.model.ActiveSession;
import com.flowops.auth.domain.model.SessionMetadata;
import com.flowops.auth.domain.model.User;
import com.flowops.auth.domain.model.UserId;
import com.flowops.auth.infrastructure.persistence.entity.AuthSessionMetadataJpaEntity;
import com.flowops.auth.infrastructure.persistence.repository.AuthSessionMetadataJpaRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class SpringSessionRegistryAdapter implements SessionRegistryPort {
    private static final String REAUTHENTICATED_UNTIL = "com.flowops.auth.reauthenticatedUntil";

    private final HttpServletRequest request;
    private final HttpServletResponse response;
    private final SecurityContextRepository securityContextRepository;
    private final AuthSessionMetadataJpaRepository sessionMetadataRepository;
    private final FindByIndexNameSessionRepository<? extends Session> sessionsByPrincipal;
    private final ResolvePermissionsPort resolvePermissionsPort;
    private final Clock clock;

    public SpringSessionRegistryAdapter(
            HttpServletRequest request,
            HttpServletResponse response,
            SecurityContextRepository securityContextRepository,
            AuthSessionMetadataJpaRepository sessionMetadataRepository,
            FindByIndexNameSessionRepository<? extends Session> sessionsByPrincipal,
            ResolvePermissionsPort resolvePermissionsPort,
            Clock clock) {
        this.request = request;
        this.response = response;
        this.securityContextRepository = securityContextRepository;
        this.sessionMetadataRepository = sessionMetadataRepository;
        this.sessionsByPrincipal = sessionsByPrincipal;
        this.resolvePermissionsPort = resolvePermissionsPort;
        this.clock = clock;
    }

    @Override
    public void open(User user, SessionMetadata metadata) {
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            existing.invalidate();
        }
        HttpSession session = request.getSession(true);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authenticationFor(user));
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        sessionMetadataRepository.save(new AuthSessionMetadataJpaEntity(
                session.getId(),
                UUID.randomUUID(),
                user.id().value(),
                metadata.ipAddress(),
                metadata.deviceSummary(),
                metadata.coarseLocation(),
                clock.instant()));

        discardTheSessionIfTheUseCaseRollsBack(session);
    }

    private void discardTheSessionIfTheUseCaseRollsBack(HttpSession session) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) {
                    return;
                }
                SecurityContextHolder.clearContext();
                try {
                    session.invalidate();
                } catch (IllegalStateException alreadyGone) {
                }
            }
        });
    }

    @Override
    public void endCurrent() {
        HttpSession session = request.getSession(false);
        if (session != null) {
            sessionMetadataRepository.deleteById(session.getId());
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    @Override
    public Optional<UserId> currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof SessionPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.of(UserId.of(principal.userId()));
    }

    @Override
    public void markReauthenticated(Instant until) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new IllegalStateException("there is no session to elevate");
        }
        session.setAttribute(REAUTHENTICATED_UNTIL, until);
    }

    @Override
    public Optional<Instant> reauthenticatedUntil() {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return Optional.empty();
        }
        return Optional.ofNullable((Instant) session.getAttribute(REAUTHENTICATED_UNTIL));
    }

    @Override
    public void endEverySessionFor(UserId userId) {
        sessionsByPrincipal.findByPrincipalName(userId.value().toString()).keySet().stream()
                .forEach(sessionsByPrincipal::deleteById);

        if (callerIs(userId)) {
            HttpSession current = request.getSession(false);
            if (current != null) {
                current.invalidate();
            }
            SecurityContextHolder.clearContext();
        }

        sessionMetadataRepository.deleteAllForUser(userId.value());
    }

    @Override
    public List<ActiveSession> sessionsOf(UserId userId) {
        Map<String, ? extends Session> live =
                sessionsByPrincipal.findByPrincipalName(userId.value().toString());
        String currentSessionId = currentSessionId();

        return sessionMetadataRepository.findByUserId(userId.value()).stream()
                .flatMap(row -> Optional.ofNullable(live.get(row.getSessionId()))
                        .filter(session -> !session.isExpired())
                        .map(session -> new ActiveSession(
                                row.getReference(),
                                session.getCreationTime(),
                                session.getLastAccessedTime(),
                                new SessionMetadata(
                                        valueOrUnknown(row.getIpAddress()),
                                        valueOrUnknown(row.getDeviceSummary()),
                                        valueOrUnknown(row.getCoarseLocation())),
                                row.getSessionId().equals(currentSessionId)))
                        .stream())
                .sorted(Comparator.comparing(ActiveSession::lastActiveAt).reversed())
                .toList();
    }

    @Override
    public Optional<UserId> endByReference(UUID reference) {
        return sessionMetadataRepository.findByReference(reference).flatMap(row -> {
            boolean sessionWasLive = sessionsByPrincipal.findById(row.getSessionId()) != null;
            sessionsByPrincipal.deleteById(row.getSessionId());
            sessionMetadataRepository.delete(row);
            return sessionWasLive ? Optional.of(UserId.of(row.getUserId())) : Optional.empty();
        });
    }

    @Override
    public Optional<UserId> ownerOf(UUID reference) {
        return sessionMetadataRepository.findByReference(reference).map(row -> UserId.of(row.getUserId()));
    }

    private boolean callerIs(UserId userId) {
        return currentUserId().filter(userId::equals).isPresent();
    }

    private String currentSessionId() {
        HttpSession session = request.getSession(false);
        return session == null ? null : session.getId();
    }

    private String valueOrUnknown(String stored) {
        return stored == null ? SessionMetadata.UNKNOWN : stored;
    }

    private Authentication authenticationFor(User user) {
        List<GrantedAuthority> authorities = resolvePermissionsPort.resolveFor(user.id()).stream()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
        return new UsernamePasswordAuthenticationToken(
                new SessionPrincipal(user.id().value(), user.email().value()), null, authorities);
    }
}
