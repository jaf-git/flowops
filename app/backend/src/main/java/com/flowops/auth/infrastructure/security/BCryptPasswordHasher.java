package com.flowops.auth.infrastructure.security;

import com.flowops.auth.domain.service.PasswordHasher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BCryptPasswordHasher implements PasswordHasher {
    private final PasswordEncoder passwordEncoder;

    public BCryptPasswordHasher(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public String hash(String rawValue) {
        return passwordEncoder.encode(rawValue);
    }

    @Override
    public boolean matches(String rawValue, String storedHash) {
        return passwordEncoder.matches(rawValue, storedHash);
    }

    @Override
    public String algorithm() {
        return "bcrypt";
    }
}
