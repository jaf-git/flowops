package com.flowops.auth.application.requestpasswordreset;

public interface RequestPasswordResetUseCase {
    void execute(RequestPasswordResetCommand command);
}
