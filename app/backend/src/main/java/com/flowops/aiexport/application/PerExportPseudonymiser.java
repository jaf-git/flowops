package com.flowops.aiexport.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

public final class PerExportPseudonymiser {
    private static final int TOKEN_LENGTH = 16;

    private final byte[] salt;

    public PerExportPseudonymiser() {
        this(freshSalt());
    }

    PerExportPseudonymiser(byte[] salt) {
        this.salt = salt.clone();
    }

    private static byte[] freshSalt() {
        byte[] drawn = new byte[32];
        new SecureRandom().nextBytes(drawn);
        return drawn;
    }

    public String of(UUID person) {
        MessageDigest digest = sha256();
        digest.update(salt);
        digest.update(person.toString().getBytes(StandardCharsets.UTF_8));
        byte[] hashed = digest.digest();

        byte[] shortened = new byte[TOKEN_LENGTH];
        System.arraycopy(hashed, 0, shortened, 0, TOKEN_LENGTH);
        return "p_" + Base64.getUrlEncoder().withoutPadding().encodeToString(shortened);
    }

    public String ofWorkspace() {
        return of(UUID.nameUUIDFromBytes("workspace".getBytes(StandardCharsets.UTF_8)));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }
}
