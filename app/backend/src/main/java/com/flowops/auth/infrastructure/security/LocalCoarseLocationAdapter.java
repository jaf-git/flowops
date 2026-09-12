package com.flowops.auth.infrastructure.security;

import com.flowops.auth.application.shared.port.ResolveCoarseLocationPort;
import com.flowops.auth.domain.model.SessionMetadata;
import org.springframework.stereotype.Component;

@Component
public class LocalCoarseLocationAdapter implements ResolveCoarseLocationPort {
    @Override
    public String resolve(String ipAddress) {
        return SessionMetadata.UNKNOWN;
    }
}
