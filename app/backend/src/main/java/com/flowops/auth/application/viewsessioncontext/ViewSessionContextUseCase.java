package com.flowops.auth.application.viewsessioncontext;

import com.flowops.auth.application.shared.SessionContext;
import java.util.Optional;

public interface ViewSessionContextUseCase {
    Optional<SessionContext> execute();
}
