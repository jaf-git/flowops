package com.flowops.process.infrastructure.auth;

import com.flowops.auth.application.viewsessioncontext.ViewSessionContextUseCase;
import com.flowops.process.application.shared.port.IdentifyCallerPort;
import com.flowops.process.domain.model.PersonId;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ProcessCallerAdapter implements IdentifyCallerPort {
    private final ViewSessionContextUseCase viewSessionContextUseCase;

    public ProcessCallerAdapter(ViewSessionContextUseCase viewSessionContextUseCase) {
        this.viewSessionContextUseCase = viewSessionContextUseCase;
    }

    @Override
    public Optional<PersonId> currentCaller() {
        return viewSessionContextUseCase.execute().map(context -> PersonId.of(context.userId()));
    }
}
