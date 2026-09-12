package com.flowops.process.infrastructure.auth;

import com.flowops.process.application.shared.port.CallerPermissionsPort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class ProcessCallerAuthoritiesAdapter implements CallerPermissionsPort {
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
