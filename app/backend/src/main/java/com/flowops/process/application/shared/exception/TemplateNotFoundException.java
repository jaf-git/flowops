package com.flowops.process.application.shared.exception;

public class TemplateNotFoundException extends RuntimeException {
    public TemplateNotFoundException() {
        super("there is no such process");
    }
}
