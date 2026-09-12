package com.flowops.auth.application.validateresettoken;

import com.flowops.auth.application.shared.UsableResetTokens;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ValidateResetTokenService implements ValidateResetTokenUseCase {
    private final UsableResetTokens usableResetTokens;
    private final Clock clock;

    public ValidateResetTokenService(UsableResetTokens usableResetTokens, Clock clock) {
        this.usableResetTokens = usableResetTokens;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public void execute(String clearToken) {
        usableResetTokens.resolve(clearToken, clock.instant());
    }
}
