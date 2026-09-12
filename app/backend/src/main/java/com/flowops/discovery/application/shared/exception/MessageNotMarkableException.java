package com.flowops.discovery.application.shared.exception;

public class MessageNotMarkableException extends RuntimeException {
    public MessageNotMarkableException() {
        super("that message cannot be marked");
    }
}
