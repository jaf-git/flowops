package com.flowops.auth.application.endpersonsessions;

import java.util.UUID;

public interface EndPersonSessionsUseCase {
    void execute(UUID personId);
}
