package com.flowops.auth.application.shared.port;

public interface GenerateResetTokenPort {
    record MintedResetToken(String clearToken, String tokenHash) {}

    MintedResetToken mint();

    String hash(String clearToken);
}
