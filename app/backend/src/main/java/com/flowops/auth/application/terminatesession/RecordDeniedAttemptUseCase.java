package com.flowops.auth.application.terminatesession;

public interface RecordDeniedAttemptUseCase {
    void execute(RecordDeniedAttemptCommand command);
}
