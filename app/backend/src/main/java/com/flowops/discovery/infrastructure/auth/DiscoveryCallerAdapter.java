package com.flowops.discovery.infrastructure.auth;

import com.flowops.auth.application.viewsessioncontext.ViewSessionContextUseCase;
import com.flowops.discovery.application.shared.port.IdentifyCallerPort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DiscoveryCallerAdapter implements IdentifyCallerPort {
    private final ViewSessionContextUseCase viewSessionContextUseCase;

    public DiscoveryCallerAdapter(ViewSessionContextUseCase viewSessionContextUseCase) {
        this.viewSessionContextUseCase = viewSessionContextUseCase;
    }

    @Override
    public Optional<UUID> currentCaller() {
        return viewSessionContextUseCase.execute().map(context -> context.userId());
    }
}
