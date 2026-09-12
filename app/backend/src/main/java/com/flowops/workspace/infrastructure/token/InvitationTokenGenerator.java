package com.flowops.workspace.infrastructure.token;

import com.flowops.workspace.application.shared.port.GenerateInvitationTokenPort;
import com.flowops.workspace.domain.model.InvitationToken;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class InvitationTokenGenerator implements GenerateInvitationTokenPort {
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    @Override
    public MintedToken mint() {
        byte[] raw = new byte[TOKEN_BYTES];
        random.nextBytes(raw);
        String clear = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        return new MintedToken(InvitationToken.of(clear), hash(clear));
    }

    @Override
    public String hash(String clear) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(clear.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
