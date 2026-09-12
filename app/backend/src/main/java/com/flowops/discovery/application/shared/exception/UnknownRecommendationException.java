package com.flowops.discovery.application.shared.exception;

public class UnknownRecommendationException extends RuntimeException {
    public UnknownRecommendationException(String message) {
        super(message);
    }
}
