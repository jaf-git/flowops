package com.flowops.auth.infrastructure.security;

import com.flowops.auth.application.shared.port.GeneratePasscodePort;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class SecurePasscodeGenerator implements GeneratePasscodePort {
    private static final int UPPER_BOUND = 1_000_000;
    private static final String FORMAT = "%06d";

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generate() {
        return String.format(FORMAT, secureRandom.nextInt(UPPER_BOUND));
    }
}
