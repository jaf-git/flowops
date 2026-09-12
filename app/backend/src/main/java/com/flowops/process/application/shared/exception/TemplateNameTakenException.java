package com.flowops.process.application.shared.exception;

public class TemplateNameTakenException extends RuntimeException {
    public TemplateNameTakenException() {
        super("a process with that name is already in use");
    }
}
