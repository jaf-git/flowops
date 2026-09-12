package com.flowops.auth.application.validateresettoken;

public interface ValidateResetTokenUseCase {
    void execute(String clearToken);
}
