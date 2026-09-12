package com.flowops.auth.infrastructure.security;

import com.flowops.auth.application.shared.port.GenerateResetTokenPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class SecureResetTokenGenerator implements GenerateResetTokenPort {
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    @Override
    public MintedResetToken mint() {
        byte[] raw = new byte[TOKEN_BYTES];
        random.nextBytes(raw);
        String clear = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        return new MintedResetToken(clear, hash(clear));
    }

    @Override
    public String hash(String clearToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(clearToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
