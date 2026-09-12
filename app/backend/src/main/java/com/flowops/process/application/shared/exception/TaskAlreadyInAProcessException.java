package com.flowops.process.application.shared.exception;

public class TaskAlreadyInAProcessException extends RuntimeException {
    public TaskAlreadyInAProcessException() {
        super("that task already belongs to a process");
    }
}
