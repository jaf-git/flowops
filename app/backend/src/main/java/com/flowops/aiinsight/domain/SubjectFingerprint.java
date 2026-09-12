package com.flowops.aiinsight.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public record SubjectFingerprint(String value) {
    public static final String UNKNOWN = "unknown";

    public static SubjectFingerprint over(List<String> shapeParts) {
        if (shapeParts.isEmpty()) {
            return new SubjectFingerprint(UNKNOWN);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : shapeParts) {
                digest.update(part.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0x1F);
            }
            return new SubjectFingerprint(HexFormat.of().formatHex(digest.digest()));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required of every Java platform", impossible);
        }
    }

    public boolean stillMatches(String then) {
        return !UNKNOWN.equals(value) && value.equals(then);
    }
}
