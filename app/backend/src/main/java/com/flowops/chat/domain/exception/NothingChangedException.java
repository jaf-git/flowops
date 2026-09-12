package com.flowops.chat.domain.exception;

public class NothingChangedException extends RuntimeException {
    public NothingChangedException() {
        super("the message is unchanged");
    }
}
