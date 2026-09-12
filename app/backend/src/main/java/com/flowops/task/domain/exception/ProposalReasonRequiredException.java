package com.flowops.task.domain.exception;

public class ProposalReasonRequiredException extends RuntimeException {
    public ProposalReasonRequiredException() {
        super("A proposed date with nothing to justify it");
    }
}
