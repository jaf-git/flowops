package com.flowops.auth.application.shared;

import com.flowops.auth.application.shared.port.ResolveCoarseLocationPort;
import com.flowops.auth.domain.model.SessionMetadata;
import com.flowops.auth.domain.service.DeviceSummary;
import org.springframework.stereotype.Component;

@Component
public class SessionMetadataFactory {
    private final ResolveCoarseLocationPort resolveCoarseLocationPort;

    public SessionMetadataFactory(ResolveCoarseLocationPort resolveCoarseLocationPort) {
        this.resolveCoarseLocationPort = resolveCoarseLocationPort;
    }

    public SessionMetadata from(ClientContext context) {
        String ipAddress = context.ipAddress() == null || context.ipAddress().isBlank()
                ? SessionMetadata.UNKNOWN
                : context.ipAddress();
        return new SessionMetadata(
                ipAddress, DeviceSummary.from(context.userAgent()), resolveCoarseLocationPort.resolve(ipAddress));
    }
}
