package com.flowops.canvas.infrastructure.auth;

import com.flowops.auth.application.viewsessioncontext.ViewSessionContextUseCase;
import com.flowops.canvas.application.shared.port.CanvasCallerPort;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CanvasCallerAdapter implements CanvasCallerPort {
    private final ViewSessionContextUseCase sessionContext;

    public CanvasCallerAdapter(ViewSessionContextUseCase sessionContext) {
        this.sessionContext = sessionContext;
    }

    @Override
    public Optional<UUID> currentCaller() {
        return sessionContext.execute().map(context -> context.userId());
    }

    @Override
    public Set<String> callerPermissions() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toUnmodifiableSet());
    }
}
