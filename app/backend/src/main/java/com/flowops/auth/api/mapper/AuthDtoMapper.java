package com.flowops.auth.api.mapper;

import com.flowops.auth.api.dto.SessionContextResponse;
import com.flowops.auth.api.dto.SessionSummaryResponse;
import com.flowops.auth.application.shared.SessionContext;
import com.flowops.auth.domain.model.ActiveSession;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AuthDtoMapper {
    private final Clock clock;

    public AuthDtoMapper(Clock clock) {
        this.clock = clock;
    }

    public SessionContextResponse toResponse(SessionContext context) {
        return new SessionContextResponse(
                context.userId(),
                context.email(),
                context.accountState().name(),
                context.permissions(),
                context.landingTarget().name(),
                clock.instant());
    }

    public SessionSummaryResponse toResponse(ActiveSession session) {
        return new SessionSummaryResponse(
                session.reference(),
                session.current(),
                session.createdAt(),
                session.lastActiveAt(),
                session.metadata().ipAddress(),
                session.metadata().deviceSummary(),
                session.metadata().coarseLocation());
    }

    public List<SessionSummaryResponse> toResponses(List<ActiveSession> sessions) {
        return sessions.stream().map(this::toResponse).toList();
    }
}
