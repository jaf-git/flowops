package com.flowops.auth.application.reauthenticate;

import java.time.Instant;

public interface ReauthenticateUseCase {
    Instant execute(ReauthenticateCommand command);
}
