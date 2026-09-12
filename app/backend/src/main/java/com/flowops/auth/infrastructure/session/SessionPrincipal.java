package com.flowops.auth.infrastructure.session;

import java.io.Serializable;
import java.util.UUID;
import org.springframework.security.core.AuthenticatedPrincipal;

public record SessionPrincipal(UUID userId, String email) implements AuthenticatedPrincipal, Serializable {
    private static final long serialVersionUID = 1L;

    @Override
    public String getName() {
        return userId.toString();
    }
}
