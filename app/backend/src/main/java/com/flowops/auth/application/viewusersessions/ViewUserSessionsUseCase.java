package com.flowops.auth.application.viewusersessions;

import com.flowops.auth.domain.model.ActiveSession;
import java.util.List;

public interface ViewUserSessionsUseCase {
    List<ActiveSession> execute(ViewUserSessionsQuery query);
}
