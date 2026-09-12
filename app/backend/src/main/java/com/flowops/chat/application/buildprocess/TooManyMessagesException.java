package com.flowops.chat.application.buildprocess;

public class TooManyMessagesException extends RuntimeException {
    public TooManyMessagesException(int limit, int asked) {
        super("A process can have at most %d steps, and %d messages were chosen.".formatted(limit, asked));
    }
}
