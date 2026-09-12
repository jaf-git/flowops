package com.flowops.chat.domain.exception;

public class NotTheAuthorException extends RuntimeException {
    public NotTheAuthorException() {
        super("only the author of a message may change it");
    }
}
