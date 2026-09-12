package com.flowops.auth.infrastructure.session;

import com.flowops.auth.application.shared.port.ResolvePermissionsPort;
import com.flowops.auth.domain.model.UserId;
import com.flowops.shared.config.SecurityChainCustomizer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class LivePermissionsFilter extends OncePerRequestFilter implements SecurityChainCustomizer {
    private final ResolvePermissionsPort resolvePermissionsPort;

    public LivePermissionsFilter(ResolvePermissionsPort resolvePermissionsPort) {
        this.resolvePermissionsPort = resolvePermissionsPort;
    }

    @Override
    public void applyTo(HttpSecurity http) {
        http.addFilterBefore(this, AuthorizationFilter.class);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        SecurityContext context = SecurityContextHolder.getContext();
        Authentication authentication = context.getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof SessionPrincipal principal) {
            List<GrantedAuthority> current = resolvePermissionsPort.resolveFor(UserId.of(principal.userId())).stream()
                    .map(SimpleGrantedAuthority::new)
                    .map(GrantedAuthority.class::cast)
                    .toList();

            UsernamePasswordAuthenticationToken refreshed =
                    new UsernamePasswordAuthenticationToken(principal, null, current);
            refreshed.setDetails(authentication.getDetails());
            context.setAuthentication(refreshed);
        }

        chain.doFilter(request, response);
    }
}
