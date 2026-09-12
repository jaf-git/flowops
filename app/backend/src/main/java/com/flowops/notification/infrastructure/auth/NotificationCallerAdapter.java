package com.flowops.notification.infrastructure.auth;

import com.flowops.auth.application.viewsessioncontext.ViewSessionContextUseCase;
import com.flowops.notification.application.shared.port.IdentifyCallerPort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class NotificationCallerAdapter implements IdentifyCallerPort {
    private final ViewSessionContextUseCase viewSessionContextUseCase;

    public NotificationCallerAdapter(ViewSessionContextUseCase viewSessionContextUseCase) {
        this.viewSessionContextUseCase = viewSessionContextUseCase;
    }

    @Override
    public Optional<UUID> currentCaller() {
        return viewSessionContextUseCase.execute().map(context -> context.userId());
    }
}
