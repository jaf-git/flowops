package com.flowops.auth.application.terminatesession;

public interface TerminateSessionUseCase {
    void execute(TerminateSessionCommand command);
}
