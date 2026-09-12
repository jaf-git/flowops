package com.flowops.aiexport.seed;

public class SeedFailedException extends RuntimeException {
    public SeedFailedException(String message) {
        super(message);
    }

    public SeedFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
