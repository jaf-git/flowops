package com.flowops.tasklib.infrastructure.auth;

import com.flowops.auth.application.shared.SessionContext;
import com.flowops.auth.application.viewsessioncontext.ViewSessionContextUseCase;
import com.flowops.tasklib.application.port.IdentifyCallerPort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TemplateCallerAdapter implements IdentifyCallerPort {
    private final ViewSessionContextUseCase viewSessionContextUseCase;

    public TemplateCallerAdapter(ViewSessionContextUseCase viewSessionContextUseCase) {
        this.viewSessionContextUseCase = viewSessionContextUseCase;
    }

    @Override
    public Optional<UUID> currentCaller() {
        return viewSessionContextUseCase.execute().map(SessionContext::userId);
    }
}
