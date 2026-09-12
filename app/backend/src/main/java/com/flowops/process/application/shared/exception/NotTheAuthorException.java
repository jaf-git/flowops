package com.flowops.process.application.shared.exception;

public class NotTheAuthorException extends RuntimeException {
    public NotTheAuthorException() {
        super("that process is somebody else's to change");
    }
}
