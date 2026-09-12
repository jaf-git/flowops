package com.flowops.process.domain.exception;

public class TemplateNameRequiredException extends RuntimeException {
    public TemplateNameRequiredException() {
        super("a template has a name; it is what somebody chooses when they start a run");
    }
}
