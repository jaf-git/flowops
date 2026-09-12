package com.flowops.chat.domain.exception;

public class MessageTooLongException extends RuntimeException {
    private final int limit;

    public MessageTooLongException(int limit, int actual) {
        super("a message may be at most " + limit + " characters; this one is " + actual);
        this.limit = limit;
    }

    public int limit() {
        return limit;
    }
}
