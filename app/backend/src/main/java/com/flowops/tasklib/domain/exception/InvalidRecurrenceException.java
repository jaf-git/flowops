package com.flowops.tasklib.domain.exception;

public class InvalidRecurrenceException extends RuntimeException {
    private final String field;

    public InvalidRecurrenceException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
