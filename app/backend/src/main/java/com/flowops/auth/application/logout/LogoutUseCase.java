package com.flowops.auth.application.logout;

public interface LogoutUseCase {
    void execute(LogoutCommand command);
}
