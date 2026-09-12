package com.flowops.auth.domain.service;

public interface PasswordHasher {
    String hash(String rawValue);

    boolean matches(String rawValue, String storedHash);

    String algorithm();
}
