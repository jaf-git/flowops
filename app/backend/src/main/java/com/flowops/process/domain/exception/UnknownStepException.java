package com.flowops.process.domain.exception;

public class UnknownStepException extends RuntimeException {
    public UnknownStepException() {
        super("that step is not part of this process");
    }
}
