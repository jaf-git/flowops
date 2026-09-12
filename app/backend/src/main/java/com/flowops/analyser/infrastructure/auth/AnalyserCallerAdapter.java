package com.flowops.analyser.infrastructure.auth;

import com.flowops.analyser.application.shared.port.IdentifyCallerPort;
import com.flowops.auth.application.viewsessioncontext.ViewSessionContextUseCase;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AnalyserCallerAdapter implements IdentifyCallerPort {
    private final ViewSessionContextUseCase viewSessionContextUseCase;

    public AnalyserCallerAdapter(ViewSessionContextUseCase viewSessionContextUseCase) {
        this.viewSessionContextUseCase = viewSessionContextUseCase;
    }

    @Override
    public Optional<UUID> currentCaller() {
        return viewSessionContextUseCase.execute().map(context -> context.userId());
    }
}
