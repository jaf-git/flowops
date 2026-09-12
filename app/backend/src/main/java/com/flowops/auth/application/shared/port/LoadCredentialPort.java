package com.flowops.auth.application.shared.port;

import com.flowops.auth.domain.model.Credential;
import com.flowops.auth.domain.model.UserId;
import java.util.Optional;

public interface LoadCredentialPort {
    Optional<Credential> loadFor(UserId userId);
}
