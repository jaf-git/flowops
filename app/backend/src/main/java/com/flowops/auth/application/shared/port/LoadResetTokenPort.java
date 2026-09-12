package com.flowops.auth.application.shared.port;

import com.flowops.auth.domain.model.ResetToken;
import java.util.Optional;

public interface LoadResetTokenPort {
    Optional<ResetToken> loadByTokenHash(String tokenHash);
}
