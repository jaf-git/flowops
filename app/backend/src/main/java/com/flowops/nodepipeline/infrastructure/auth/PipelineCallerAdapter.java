package com.flowops.nodepipeline.infrastructure.auth;

import com.flowops.auth.application.viewsessioncontext.ViewSessionContextUseCase;
import com.flowops.nodepipeline.application.port.PipelineCallerPort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PipelineCallerAdapter implements PipelineCallerPort {
    private final ViewSessionContextUseCase viewSessionContextUseCase;

    public PipelineCallerAdapter(ViewSessionContextUseCase viewSessionContextUseCase) {
        this.viewSessionContextUseCase = viewSessionContextUseCase;
    }

    @Override
    public Optional<UUID> currentCaller() {
        return viewSessionContextUseCase.execute().map(context -> context.userId());
    }
}
