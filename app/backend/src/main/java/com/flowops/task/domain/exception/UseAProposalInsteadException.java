package com.flowops.task.domain.exception;

public class UseAProposalInsteadException extends RuntimeException {
    public UseAProposalInsteadException() {
        super("work has begun, so the date is renegotiated rather than re-set");
    }
}
