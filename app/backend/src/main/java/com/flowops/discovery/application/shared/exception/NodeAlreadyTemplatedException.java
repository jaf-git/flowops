package com.flowops.discovery.application.shared.exception;

public class NodeAlreadyTemplatedException extends RuntimeException {
    public NodeAlreadyTemplatedException(String message) {
        super(message);
    }
}
