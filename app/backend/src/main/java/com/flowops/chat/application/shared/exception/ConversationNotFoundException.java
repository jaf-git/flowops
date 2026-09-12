package com.flowops.chat.application.shared.exception;

public class ConversationNotFoundException extends RuntimeException {
    public ConversationNotFoundException() {
        super("no such conversation");
    }
}
