package com.flowops.auth.application.viewownsessions;

import com.flowops.auth.domain.model.ActiveSession;
import java.util.List;

public interface ViewOwnSessionsUseCase {
    List<ActiveSession> execute();
}
