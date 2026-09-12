package com.flowops.auth.application.shared.port;

import com.flowops.auth.domain.model.Credential;
import com.flowops.auth.domain.model.UserId;

public interface SaveCredentialPort {
    void save(Credential credential);

    void deleteFor(UserId user);
}
