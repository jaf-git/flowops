package com.flowops.process.application.shared.exception;

public class StepTemplateUnavailableException extends RuntimeException {
    private final int position;

    public StepTemplateUnavailableException(int position) {
        super("step " + (position + 1) + " references a task template that cannot be read");
        this.position = position;
    }

    public int position() {
        return position;
    }
}
