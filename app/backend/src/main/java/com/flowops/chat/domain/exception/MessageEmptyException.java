package com.flowops.chat.domain.exception;

public class MessageEmptyException extends RuntimeException {
    public MessageEmptyException() {
        super("a message cannot be empty");
    }
}
