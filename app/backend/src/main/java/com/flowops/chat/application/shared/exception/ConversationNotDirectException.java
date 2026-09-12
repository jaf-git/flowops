package com.flowops.chat.application.shared.exception;

public class ConversationNotDirectException extends RuntimeException {
    public ConversationNotDirectException() {
        super("This is not a conversation with one other person.");
    }
}
