package com.flowops.auth.application.shared;

import com.flowops.auth.application.shared.exception.ResetTokenNotUsableException;
import com.flowops.auth.application.shared.port.GenerateResetTokenPort;
import com.flowops.auth.application.shared.port.LoadResetTokenPort;
import com.flowops.auth.application.shared.port.LoadUserPort;
import com.flowops.auth.domain.model.ResetToken;
import com.flowops.auth.domain.model.User;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class UsableResetTokens {
    public record Resolved(ResetToken token, User user) {}

    private final GenerateResetTokenPort generateResetTokenPort;
    private final LoadResetTokenPort loadResetTokenPort;
    private final LoadUserPort loadUserPort;

    public UsableResetTokens(
            GenerateResetTokenPort generateResetTokenPort,
            LoadResetTokenPort loadResetTokenPort,
            LoadUserPort loadUserPort) {
        this.generateResetTokenPort = generateResetTokenPort;
        this.loadResetTokenPort = loadResetTokenPort;
        this.loadUserPort = loadUserPort;
    }

    public Resolved resolve(String clearToken, Instant now) {
        if (clearToken == null || clearToken.isBlank()) {
            throw new ResetTokenNotUsableException();
        }
        ResetToken token = loadResetTokenPort
                .loadByTokenHash(generateResetTokenPort.hash(clearToken))
                .orElseThrow(ResetTokenNotUsableException::new);
        if (!token.isUsable(now)) {
            throw new ResetTokenNotUsableException();
        }
        Optional<User> user = loadUserPort.loadById(token.userId());
        if (user.isEmpty() || !user.get().canAuthenticate()) {
            throw new ResetTokenNotUsableException();
        }
        return new Resolved(token, user.get());
    }
}
