package com.flowops.workspace.infrastructure.auth;

import com.flowops.workspace.application.shared.port.CallerPermissionsPort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CallerAuthoritiesAdapter implements CallerPermissionsPort {
    @Override
    public boolean callerHolds(String permission) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(permission::equals);
    }
}
