package com.flowops.workspace.infrastructure.auth;

import com.flowops.auth.application.shared.SensitiveActionGuard;
import com.flowops.workspace.application.shared.exception.ReauthenticationRequiredException;
import com.flowops.workspace.application.shared.port.RequireReauthenticationPort;
import org.springframework.stereotype.Component;

@Component
public class ReauthenticationAdapter implements RequireReauthenticationPort {
    private final SensitiveActionGuard sensitiveActionGuard;

    public ReauthenticationAdapter(SensitiveActionGuard sensitiveActionGuard) {
        this.sensitiveActionGuard = sensitiveActionGuard;
    }

    @Override
    public void requireRecentReauthentication() {
        try {
            sensitiveActionGuard.requireRecentReauthentication();
        } catch (com.flowops.auth.application.shared.exception.ReauthenticationRequiredException challenge) {
            throw new ReauthenticationRequiredException();
        }
    }
}
