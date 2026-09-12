package com.flowops.auth.domain.service;

public final class ReversibleHasher implements PasswordHasher {
    private static final String PREFIX = "reversed:";

    private int hashCalls;
    private int matchCalls;

    @Override
    public String hash(String rawValue) {
        hashCalls++;
        return PREFIX + rawValue;
    }

    @Override
    public boolean matches(String rawValue, String storedHash) {
        matchCalls++;
        return (PREFIX + rawValue).equals(storedHash);
    }

    @Override
    public String algorithm() {
        return "reversible";
    }

    public int hashCalls() {
        return hashCalls;
    }

    public int matchCalls() {
        return matchCalls;
    }

    public void resetCounts() {
        hashCalls = 0;
        matchCalls = 0;
    }
}
