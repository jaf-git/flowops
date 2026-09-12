package com.flowops.chat.application.buildprocess;

public class TooFewMessagesException extends RuntimeException {
    public TooFewMessagesException() {
        super("Choose at least two messages to build a process from.");
    }
}
