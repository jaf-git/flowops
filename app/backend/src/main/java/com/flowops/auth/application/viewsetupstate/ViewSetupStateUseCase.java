package com.flowops.auth.application.viewsetupstate;

import java.util.Optional;

public interface ViewSetupStateUseCase {
    Optional<SetupState> execute();
}
