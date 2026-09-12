package com.flowops.chat.application.shared.exception;

public class CounterpartNotActiveException extends RuntimeException {
    public CounterpartNotActiveException() {
        super("that person is no longer active in this workspace");
    }
}
