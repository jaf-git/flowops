package com.flowops.chatassist.infrastructure.auth;

import com.flowops.auth.application.shared.SessionContext;
import com.flowops.auth.application.viewsessioncontext.ViewSessionContextUseCase;
import com.flowops.chatassist.application.port.IdentifyCallerPort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ChatAssistCallerAdapter implements IdentifyCallerPort {
    private final ViewSessionContextUseCase sessions;

    public ChatAssistCallerAdapter(ViewSessionContextUseCase sessions) {
        this.sessions = sessions;
    }

    @Override
    public Optional<UUID> currentCaller() {
        return sessions.execute().map(SessionContext::userId);
    }
}
