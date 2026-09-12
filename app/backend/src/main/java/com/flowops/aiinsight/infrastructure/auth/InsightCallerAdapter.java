package com.flowops.aiinsight.infrastructure.auth;

import com.flowops.aiinsight.application.port.IdentifyCallerPort;
import com.flowops.auth.application.shared.SessionContext;
import com.flowops.auth.application.viewsessioncontext.ViewSessionContextUseCase;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class InsightCallerAdapter implements IdentifyCallerPort {
    private final ViewSessionContextUseCase sessions;

    public InsightCallerAdapter(ViewSessionContextUseCase sessions) {
        this.sessions = sessions;
    }

    @Override
    public Optional<UUID> currentCaller() {
        return sessions.execute().map(SessionContext::userId);
    }
}
