package com.flowops.chat.domain.exception;

public class MessageDeletedException extends RuntimeException {
    public MessageDeletedException() {
        super("this message has been deleted");
    }
}
