package com.flowops.task.infrastructure.auth;

import com.flowops.auth.application.viewsessioncontext.ViewSessionContextUseCase;
import com.flowops.task.application.shared.port.IdentifyCallerPort;
import com.flowops.task.domain.model.PersonId;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class TaskCallerAdapter implements IdentifyCallerPort {
    private final ViewSessionContextUseCase viewSessionContextUseCase;

    public TaskCallerAdapter(ViewSessionContextUseCase viewSessionContextUseCase) {
        this.viewSessionContextUseCase = viewSessionContextUseCase;
    }

    @Override
    public Optional<PersonId> currentCaller() {
        return viewSessionContextUseCase.execute().map(context -> PersonId.of(context.userId()));
    }
}
