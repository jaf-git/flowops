package com.flowops.process.domain.exception;

public class StepNeedsTaskTemplateException extends RuntimeException {
    private final int position;

    public StepNeedsTaskTemplateException(int position) {
        super("step " + (position + 1) + " names no task template");
        this.position = position;
    }

    public int position() {
        return position;
    }
}
