package com.flowops.discovery.application.shared.exception;

public class RecommendationAlreadyDecidedException extends RuntimeException {
    public RecommendationAlreadyDecidedException(String message) {
        super(message);
    }
}
