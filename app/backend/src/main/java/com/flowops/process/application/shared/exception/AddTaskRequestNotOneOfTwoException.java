package com.flowops.process.application.shared.exception;

public class AddTaskRequestNotOneOfTwoException extends RuntimeException {
    public AddTaskRequestNotOneOfTwoException() {
        super("supply either an existing task or a new one, never both");
    }
}
