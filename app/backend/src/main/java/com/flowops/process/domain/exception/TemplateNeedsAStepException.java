package com.flowops.process.domain.exception;

public class TemplateNeedsAStepException extends RuntimeException {
    public TemplateNeedsAStepException() {
        super("a template with no work is not a process");
    }
}
