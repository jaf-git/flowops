package com.flowops.task.domain.exception;

public class DeadlineRequiredToStartException extends RuntimeException {
    public DeadlineRequiredToStartException() {
        super("work cannot begin without a deadline; set one first");
    }
}
