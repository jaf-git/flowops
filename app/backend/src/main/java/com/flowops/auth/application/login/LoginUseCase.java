package com.flowops.auth.application.login;

import com.flowops.auth.application.shared.SessionContext;

public interface LoginUseCase {
    SessionContext execute(LoginCommand command);
}
